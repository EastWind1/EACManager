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
	"gorm.io/gorm/clause"
)

var (
	passwordPattern = regexp.MustCompile(`[a-zA-Z0-9*\-_^~]+`)
	usernamePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]{3,31}$`)
	phonePattern    = regexp.MustCompile(`^1[3-9]\d{9}$`)
	emailPattern    = regexp.MustCompile(`^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$`)
)

// Service 用户服务
type Service struct {
	userRepo    *Repository
	jwtSvc      *JWTService
	securitySvc *security.Service
}

// NewService 创建用户服务实例
func NewService(userRepo *Repository, jwtSvc *JWTService, securitySvc *security.Service) *Service {
	return &Service{
		userRepo:    userRepo,
		jwtSvc:      jwtSvc,
		securitySvc: securitySvc,
	}
}

// IsInitializationRequired 是否需要初始化管理员
func (s *Service) IsInitializationRequired(ctx context.Context) (bool, error) {
	hasAdmin, err := s.securitySvc.HasAdminUser(ctx)
	if err != nil {
		return false, err
	}
	return !hasAdmin, nil
}

// validPassword 校验密码
func (s *Service) validPassword(password *string) error {
	if password == nil || len(*password) < 10 {
		return errs.NewBizError("密码不能小于 10 位")
	}
	if len(*password) > 32 {
		return errs.NewBizError("密码不能大于 32 位")
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
	if err := validateInit(dto); err != nil {
		return nil, err
	}

	var res *DTO
	err := s.userRepo.Transaction(ctx, func(tx context.Context) error {
		var role security.Role
		if err := s.userRepo.GetDB(tx).Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("code = ?", security.AdminRoleCode).Take(&role).Error; err != nil {
			return errs.NewBizError("管理员角色不存在，无法初始化")
		}

		hasAdmin, err := s.securitySvc.HasAdminUser(tx)
		if err != nil {
			return err
		}
		if hasAdmin {
			return errs.NewBizError("系统已初始化，不能重复创建管理员")
		}

		username := strings.TrimSpace(dto.Username)
		exists, err := s.userRepo.ExistsByUsername(tx, username)
		if err != nil {
			return err
		}
		if exists {
			return errs.NewBizError("用户名已存在")
		}

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
		if err := s.userRepo.Create(tx, &user); err != nil {
			return err
		}
		if err := s.securitySvc.SetUserRoles(tx, user.ID, []string{security.AdminRoleCode}); err != nil {
			return err
		}

		resultDTO := user.ToDTO()
		resultDTO.Roles, err = s.securitySvc.UserRoles(tx, user.ID)
		if err != nil {
			return err
		}
		resultDTO.Permissions, err = s.securitySvc.UserPermissions(tx, user.ID)
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

func validateInit(dto *InitDTO) error {
	if dto == nil || !usernamePattern.MatchString(strings.TrimSpace(dto.Username)) {
		return errs.NewBizError("用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线")
	}
	password := dto.Password
	if len(password) < 8 || len(password) > 72 ||
		strings.ContainsAny(password, " \t\r\n") ||
		!strings.ContainsAny(password, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ") ||
		!strings.ContainsAny(password, "0123456789") {
		return errs.NewBizError("密码必须为 8-72 位，且同时包含字母和数字，不能包含空白字符")
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

// Login 用户登录
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

// GetAll 获取用户列表
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
		userIDs := make([]uint, 0, len(page.Items))
		for _, u := range page.Items {
			userIDs = append(userIDs, u.ID)
		}
		dtos := make([]DTO, 0, len(page.Items))
		for _, u := range page.Items {
			dto := u.ToDTO()
			if dto.Roles == nil {
				dto.Roles = []security.RoleDTO{}
			}
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

// Create 创建用户
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

	var res *DTO
	err = s.userRepo.Transaction(ctx, func(tx context.Context) error {
		if slices.Contains(roleCodes, security.AdminRoleCode) {
			isAdmin, err := s.securitySvc.HasRole(tx, curUser.GetID(), security.AdminRoleCode)
			if err != nil {
				return err
			}
			if !isAdmin {
				return errs.NewBizError("无权创建管理员")
			}
		}

		exists, err := s.userRepo.ExistsByUsername(tx, dto.Username)
		if err != nil {
			return err
		}
		if exists {
			return errs.NewBizError("用户名已存在")
		}

		newUser := dto.ToEntity()
		ep, err := s.encryptPassword(newUser.Password)
		if err != nil {
			return err
		}
		newUser.Password = ep
		if err = s.userRepo.Create(tx, newUser); err != nil {
			return err
		}
		if err = s.securitySvc.SetUserRoles(tx, newUser.ID, roleCodes); err != nil {
			return err
		}
		resultDTO := newUser.ToDTO()
		resultDTO.Roles, err = s.securitySvc.UserRoles(tx, newUser.ID)
		if err != nil {
			return err
		}
		res = resultDTO
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

// Update 更新用户
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
	err = s.userRepo.Transaction(ctx, func(tx context.Context) error {
		user, err := s.userRepo.FindByID(tx, dto.ID)
		if err != nil {
			return err
		}
		if user == nil {
			return errs.NewBizError("用户不存在")
		}
		if user.Username != dto.Username {
			return errs.NewBizError("用户名不能修改")
		}
		currentRoles, err := s.securitySvc.RoleCodes(tx, user.ID)
		if err != nil {
			return err
		}
		requestedRoles := roleCodes(dto.Roles)
		if dto.Roles == nil {
			requestedRoles = currentRoles
		}
		if !isAdmin && !sameStrings(requestedRoles, currentRoles) {
			return errs.NewBizError("无权更改角色")
		}
		if isAdmin && dto.Roles != nil {
			if err = s.securitySvc.SetUserRoles(tx, user.ID, requestedRoles); err != nil {
				return err
			}
		}
		if dto.Password != nil {
			if *dto.Password == "" {
				return errs.NewBizError("密码不能为空")
			}
			passwordByte := []byte(*dto.Password)
			if len(passwordByte) > 72 {
				return errs.NewBizError("密码过长")
			}
			hashedPassword, err := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
			if err != nil {
				return errs.Wrap(err)
			}
			user.Password = string(hashedPassword)
		}
		user.Name = dto.Name
		user.Phone = dto.Phone
		user.Email = dto.Email
		if err = s.userRepo.Updates(tx, user); err != nil {
			return err
		}
		resultDTO := user.ToDTO()
		resultDTO.Roles, err = s.securitySvc.UserRoles(tx, user.ID)
		if err != nil {
			return err
		}
		res = resultDTO
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

// Disable 禁用用户
func (s *Service) Disable(ctx context.Context, username string) error {
	return s.userRepo.Transaction(ctx, func(tx context.Context) error {
		user, err := s.userRepo.FindByUsername(tx, username)
		if err != nil {
			return err
		}
		user.Disabled = true
		return s.userRepo.Updates(tx, user)
	})
}

// FindByUsername 加载用户信息
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
