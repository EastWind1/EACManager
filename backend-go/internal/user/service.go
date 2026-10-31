package user

import (
	"backend-go/internal/security"
	"backend-go/pkg/auth"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"regexp"
	"slices"
	"strings"

	"github.com/gofiber/fiber/v3/log"
	"golang.org/x/crypto/bcrypt"
)

var (
	passwordPattern = regexp.MustCompile(`^[a-zA-Z0-9*\-_^~]+$`)
	usernamePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]{3,31}$`)
	phonePattern    = regexp.MustCompile(`^1[3-9]\d{9}$`)
	emailPattern    = regexp.MustCompile(`^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$`)
)

type Service struct {
	userRepo    *Repository
	jwtSvc      *JWTService
	securitySvc *security.Service
}

func NewService(userRepo *Repository, jwtSvc *JWTService, securitySvc *security.Service) *Service {
	return &Service{
		userRepo:    userRepo,
		jwtSvc:      jwtSvc,
		securitySvc: securitySvc,
	}
}

func (s *Service) IsInitializationRequired(ctx context.Context) (bool, error) {
	hasAdmin, err := s.securitySvc.HasAdminUser(ctx)
	if err != nil {
		return false, err
	}
	return !hasAdmin, nil
}

func (s *Service) validPassword(password *string) error {
	if password == nil || len(*password) < 10 {
		return errs.NewBizError("密码不能小于 10 位")
	}
	if len(*password) > 72 {
		return errs.NewBizError("密码不能大于 72 位")
	}
	if !passwordPattern.MatchString(*password) {
		return errs.NewBizError("密码只能包含大写、小写字母、数字以及 *-_^~")
	}
	lc := 0
	uc := 0
	nc := 0
	oc := 0
	for _, r := range *password {
		if 'a' <= r && r <= 'z' {
			lc++
		} else if 'A' <= r && r <= 'Z' {
			uc++
		} else if '0' <= r && r <= '9' {
			nc++
		} else {
			oc++
		}
	}
	sum := lc + uc + nc + oc
	if sum < 2 {
		return errs.NewBizError("密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种")
	}
	return nil
}

// roleDTOs 把角色实体转成 DTO，空集合返回空切片而不是 nil（响应里 roles 始终是数组）
func roleDTOs(roles []security.Role) []security.RoleDTO {
	res := make([]security.RoleDTO, 0, len(roles))
	for _, role := range roles {
		res = append(res, *role.ToDTO())
	}
	return res
}

func (s *Service) encryptPassword(password string) (string, error) {
	passwordByte := []byte(password)
	hashedPassword, e := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
	if e != nil {
		return "", errs.Wrap(e)
	}
	return string(hashedPassword), nil
}

// Initialize 创建首个管理员；仅当系统不存在管理员用户时允许
func (s *Service) Initialize(ctx context.Context, dto *InitDTO) (*DTO, error) {
	if err := s.validateInit(dto); err != nil {
		return nil, err
	}

	var res *DTO
	err := s.userRepo.Transaction(ctx, func(txCtx context.Context) error {

		// 行锁取管理员角色：并发的两次初始化在这里串行化（加锁细节留在 security 仓储里）
		role, err := s.securitySvc.RoleByCodeForUpdate(txCtx, security.AdminRoleCode)
		if err != nil {
			return err
		}
		if role == nil {
			return errs.NewBizError("管理员角色不存在，无法初始化")
		}

		hasAdmin, err := s.securitySvc.HasAdminUser(txCtx)
		if err != nil {
			return err
		}
		if hasAdmin {
			return errs.NewBizError("系统已初始化，不能重复创建管理员")
		}

		username := strings.TrimSpace(dto.Username)
		hashed, err := bcrypt.GenerateFromPassword([]byte(sha256Hex(dto.Password+":"+username)), bcrypt.DefaultCost)
		if err != nil {
			return errs.Wrap(err)
		}
		user := User{
			Username: username,
			Password: string(hashed),
			Name:     strings.TrimSpace(dto.Name),
			Phone:    strings.TrimSpace(dto.Phone),
			Email:    strings.TrimSpace(dto.Email),
			Disabled: false,
		}
		// 用户名唯一索引兜底（Create 会转成「用户名已存在」），行锁已保证并发初始化只有一次
		if err := s.userRepo.Create(txCtx, &user); err != nil {
			return err
		}
		granted, err := s.securitySvc.SetUserRoles(txCtx, user.ID, []string{security.AdminRoleCode})
		if err != nil {
			return err
		}

		resultDTO := user.ToDTO()
		resultDTO.Roles = granted
		resultDTO.Permissions, err = s.securitySvc.UserPermissions(txCtx, user.ID)
		if err != nil {
			return err
		}
		res = resultDTO
		return nil
	})
	if err != nil {
		return nil, err
	}
	log.Infof("系统初始化管理员: %v", res.Username)
	return res, nil
}

func (s *Service) validateInit(dto *InitDTO) error {
	if dto == nil || !usernamePattern.MatchString(strings.TrimSpace(dto.Username)) {
		return errs.NewBizError("用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线")
	}
	password := dto.Password
	if err := s.validPassword(&password); err != nil {
		return err
	}
	name := strings.TrimSpace(dto.Name)
	if name == "" || len([]rune(name)) > 64 {
		return errs.NewBizError("姓名不能为空且不能超过 64 个字符")
	}
	phone := strings.TrimSpace(dto.Phone)
	if phone != "" && !phonePattern.MatchString(phone) {
		return errs.NewBizError("手机号格式不正确")
	}
	email := strings.TrimSpace(dto.Email)
	if email != "" && (len(email) > 128 || !emailPattern.MatchString(email)) {
		return errs.NewBizError("邮箱格式不正确")
	}
	return nil
}

func sha256Hex(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func (s *Service) Login(ctx context.Context, username, password string) (*LoginResult, error) {
	user, err := s.userRepo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if user.Disabled {
		return nil, errs.NewBizError("用户已禁用")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		log.Warnf("登录失败: %v", username)
		return nil, errs.NewBizError("用户名或密码错误")
	}
	token, err := s.jwtSvc.GenerateToken(username)
	if err != nil {
		return nil, err
	}
	dto := user.ToDTO()
	dto.Roles, err = s.securitySvc.UserRoles(ctx, user.ID)
	if err != nil {
		return nil, err
	}
	dto.Permissions, err = s.securitySvc.UserPermissions(ctx, user.ID)
	if err != nil {
		return nil, err
	}
	log.Infof("用户登录: %v", username)
	return &LoginResult{Token: token, User: *dto}, nil
}

func (s *Service) GetAll(ctx context.Context, queryParam *result.QueryParam) (*result.PageResult[DTO], error) {
	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, curUser.GetID(), security.AdminRoleCode)
	if err != nil {
		return nil, err
	}
	if isAdmin {
		page, err := s.userRepo.FindAllEnabled(ctx, queryParam)
		if err != nil {
			return nil, err
		}
		// 整页用户的角色一次取回，避免每用户两次查询
		userIDs := make([]uint, 0, len(page.Items))
		for _, u := range page.Items {
			userIDs = append(userIDs, u.ID)
		}
		rolesByUser, err := s.userRepo.FindRolesByUserIDs(ctx, userIDs)
		if err != nil {
			return nil, err
		}
		dtos := make([]DTO, 0, len(page.Items))
		for _, u := range page.Items {
			dto := u.ToDTO()
			dto.Roles = roleDTOs(rolesByUser[u.ID])
			dtos = append(dtos, *dto)
		}
		return &result.PageResult[DTO]{
			Items:      dtos,
			TotalCount: page.TotalCount,
			PageIndex:  page.PageIndex,
			PageSize:   page.PageSize,
			TotalPages: page.TotalPages,
		}, nil
	}

	// 非管理员只能查自己
	user, err := s.userRepo.FindByID(ctx, curUser.GetID())
	if err != nil {
		return nil, err
	}
	dtos := make([]DTO, 0, 1)
	if user != nil {
		dto := user.ToDTO()
		dto.Roles, err = s.securitySvc.UserRoles(ctx, user.ID)
		if err != nil {
			return nil, err
		}
		dtos = append(dtos, *dto)
	}
	return result.NewPageResult(dtos, len(dtos), 0, 1), nil
}

func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if err := s.validPassword(dto.Password); err != nil {
		return nil, err
	}
	if dto.Username == "" {
		return nil, errs.NewBizError("用户名不能为空")
	}
	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	roleCodes := roleCodes(dto.Roles)
	if len(roleCodes) == 0 {
		roleCodes = []string{"USER"}
	}

	// 非管理员只能授予自己已持有的角色，避免借 user:create 越权拿到更高权限的角色
	isAdmin, err := s.securitySvc.HasRole(ctx, curUser.GetID(), security.AdminRoleCode)
	if err != nil {
		return nil, err
	}
	if !isAdmin {
		callerRoles, err := s.securitySvc.RoleCodes(ctx, curUser.GetID())
		if err != nil {
			return nil, err
		}
		if !containsAll(callerRoles, roleCodes) {
			return nil, errs.NewBizError("无权授予未持有的角色")
		}
	}

	var res *DTO
	err = s.userRepo.Transaction(ctx, func(txCtx context.Context) error {

		newUser := dto.ToEntity()
		ep, err := s.encryptPassword(newUser.Password)
		if err != nil {
			return err
		}
		newUser.Password = ep
		// 用户名唯一索引即判重，不再先查后插（并发下先查后插本身也挡不住重复）
		if err = s.userRepo.Create(txCtx, newUser); err != nil {
			return err
		}
		granted, err := s.securitySvc.SetUserRoles(txCtx, newUser.ID, roleCodes)
		if err != nil {
			return err
		}
		// 角色就是刚写入的那一批，直接回填，省掉再查一次库
		resultDTO := newUser.ToDTO()
		resultDTO.Roles = granted
		res = resultDTO
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *Service) Update(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("id 不能为空")
	}
	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, curUser.GetID(), security.AdminRoleCode)
	if err != nil {
		return nil, err
	}
	if !isAdmin && curUser.GetID() != dto.ID {
		return nil, errs.NewAuthError("无权限修改其他用户信息")
	}
	var res *DTO
	err = s.userRepo.Transaction(ctx, func(txCtx context.Context) error {

		user, err := s.userRepo.FindByID(txCtx, dto.ID)
		if err != nil {
			return err
		}
		if user == nil {
			return errs.NewBizError("用户不存在")
		}
		if user.Username != dto.Username {
			return errs.NewBizError("用户名不能修改")
		}
		currentRoles, err := s.securitySvc.RoleCodes(txCtx, user.ID)
		if err != nil {
			return err
		}
		requestedRoles := roleCodes(dto.Roles)
		if dto.Roles == nil {
			requestedRoles = currentRoles
		}
		var password *string
		var granted []security.RoleDTO
		if !isAdmin && !sameStrings(requestedRoles, currentRoles) {
			return errs.NewBizError("无权更改角色")
		}
		if isAdmin && dto.Roles != nil {
			// 不允许把最后一名启用的管理员降级，否则系统将无人可管理
			if slices.Contains(currentRoles, security.AdminRoleCode) &&
				!slices.Contains(requestedRoles, security.AdminRoleCode) {
				count, err := s.userRepo.CountEnabledUsersWithRole(txCtx, security.AdminRoleCode)
				if err != nil {
					return err
				}
				if count <= 1 {
					return errs.NewBizError("必须保留至少一个启用的管理员")
				}
			}
			// 整体替换角色；返回值即为写入后的角色，省掉后面再查一次
			if granted, err = s.securitySvc.SetUserRoles(txCtx, user.ID, requestedRoles); err != nil {
				return err
			}
		}
		if dto.Password != nil {
			if err := s.validPassword(dto.Password); err != nil {
				return err
			}
			hashedPassword, err := bcrypt.GenerateFromPassword([]byte(*dto.Password), bcrypt.DefaultCost)
			if err != nil {
				return errs.Wrap(err)
			}
			user.Password = string(hashedPassword)
			password = &user.Password
		}
		user.Name = dto.Name
		user.Phone = dto.Phone
		user.Email = dto.Email
		// 只更新资料与（可选的）密码列，不回写整行
		if err = s.userRepo.UpdateProfile(txCtx, user, password); err != nil {
			return err
		}
		resultDTO := user.ToDTO()
		// 角色刚被整体替换过就直接用写入结果，否则再取一次
		if granted != nil {
			resultDTO.Roles = granted
		} else {
			resultDTO.Roles, err = s.securitySvc.UserRoles(txCtx, user.ID)
			if err != nil {
				return err
			}
		}
		res = resultDTO
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *Service) Disable(ctx context.Context, username string) error {
	users, err := s.userRepo.FindAll(ctx, "username = ?", username)
	if err != nil {
		return err
	}
	if len(users) == 0 {
		return errs.NewBizError("用户不存在")
	}
	target := users[0]

	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return err
	}
	if target.ID == curUser.GetID() {
		return errs.NewBizError("不能禁用自己")
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, target.ID, security.AdminRoleCode)
	if err != nil {
		return err
	}
	if isAdmin {
		count, err := s.userRepo.CountEnabledUsersWithRole(ctx, security.AdminRoleCode)
		if err != nil {
			return err
		}
		if count <= 1 {
			return errs.NewBizError("必须保留至少一个启用的管理员")
		}
	}
	disabled, err := s.userRepo.Disable(ctx, username)
	if err != nil {
		return err
	}
	if !disabled {
		return errs.NewBizError("用户不存在")
	}
	return nil
}

func (s *Service) FindByUsername(ctx context.Context, username string) (*User, error) {
	return s.userRepo.FindByUsername(ctx, username)
}

func roleCodes(roles []security.RoleDTO) []string {
	codes := make([]string, 0, len(roles))
	for _, role := range roles {
		codes = append(codes, role.Code)
	}
	return codes
}

func sameStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	set := make(map[string]struct{}, len(a))
	for _, value := range a {
		set[value] = struct{}{}
	}
	for _, value := range b {
		if _, ok := set[value]; !ok {
			return false
		}
	}
	return true
}

// containsAll 判断 owned 是否包含 requested 的全部元素
func containsAll(owned, requested []string) bool {
	set := make(map[string]struct{}, len(owned))
	for _, value := range owned {
		set[value] = struct{}{}
	}
	for _, value := range requested {
		if _, ok := set[value]; !ok {
			return false
		}
	}
	return true
}
