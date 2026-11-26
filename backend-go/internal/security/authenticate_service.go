package security

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"fmt"
	"regexp"
	"slices"
	"strings"
	"time"

	"github.com/gofiber/fiber/v3/log"
	"golang.org/x/crypto/bcrypt"
)

var (
	accountUsernamePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]{3,31}$`)
	accountPasswordPattern = regexp.MustCompile(`^[a-zA-Z0-9*\-_^~]+$`)
)

type AuthenticateService struct {
	repo        *AccountRepository
	tokenSvc    *TokenService
	securitySvc *AuthorizeService
	cache       cache.Cache
	authUserTTL time.Duration
}

func newAuthenticateService(tokenSvc *TokenService, repo *AccountRepository, securitySvc *AuthorizeService,
	cache cache.Cache, authUserTTL time.Duration) *AuthenticateService {
	return &AuthenticateService{
		repo:        repo,
		tokenSvc:    tokenSvc,
		securitySvc: securitySvc,
		cache:       cache,
		authUserTTL: authUserTTL,
	}
}

// Logout 把当前 token 加入黑名单，直到自然过期。
func (s *AuthenticateService) Logout(token string) {
	s.tokenSvc.Revoke(token)
}

func (s *AuthenticateService) IsInitializationRequired(ctx context.Context) (bool, error) {
	hasAdmin, err := s.securitySvc.HasAdminUser(ctx)
	if err != nil {
		return false, err
	}
	return !hasAdmin, nil
}

func (s *AuthenticateService) Login(ctx context.Context, username, password string) (*LoginResult, error) {
	account, err := s.repo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errs.NewBizError("用户名或密码错误")
	}
	if account.Disabled {
		return nil, errs.NewBizError("用户已禁用")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(account.Password), []byte(password)); err != nil {
		log.Warnf("登录失败: %v", username)
		return nil, errs.NewBizError("用户名或密码错误")
	}
	token, err := s.tokenSvc.Issue(account.ID)
	if err != nil {
		return nil, err
	}
	dto, err := s.toDTO(ctx, account)
	if err != nil {
		return nil, err
	}
	log.Infof("用户登录: %v", username)
	return &LoginResult{Token: token, Account: *dto}, nil
}

func (s *AuthenticateService) Initialize(ctx context.Context, username, password string) (*AuthorityUser, error) {
	username = strings.TrimSpace(username)
	if err := s.validUsername(username); err != nil {
		return nil, err
	}
	if err := s.validPassword(&password); err != nil {
		return nil, err
	}
	var res *AuthorityUser
	err := s.repo.Transaction(ctx, func(txCtx context.Context) error {
		role, err := s.securitySvc.RoleByCodeForUpdate(txCtx, AdminRoleCode)
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
		hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if err != nil {
			return errs.Wrap(err)
		}
		account := Account{Username: username, Password: string(hashed), Disabled: false}
		if err = s.repo.Create(txCtx, &account); err != nil {
			return err
		}
		granted, err := s.securitySvc.SetUserRoles(txCtx, account.ID, []string{AdminRoleCode})
		if err != nil {
			return err
		}
		res = account.ToDTO()
		res.Roles = granted
		res.Permissions, err = s.securitySvc.UserPermissions(txCtx, account.ID)
		return err
	})
	if err != nil {
		return nil, err
	}
	log.Infof("系统初始化管理员: %v", username)
	return res, nil
}

func (s *AuthenticateService) CreateAccount(ctx context.Context, username, password string, roles []RoleDTO) (*AuthorityUser, error) {
	username = strings.TrimSpace(username)
	if err := s.validUsername(username); err != nil {
		return nil, err
	}
	if err := s.validPassword(&password); err != nil {
		return nil, err
	}
	roleCodes := roleCodes(roles)
	if len(roleCodes) == 0 {
		roleCodes = []string{"USER"}
	}
	if err := s.assertCanAssignRoles(ctx, roleCodes); err != nil {
		return nil, err
	}
	// 并发窗口由唯一索引兜底。
	existing, err := s.repo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return nil, errs.NewBizError("用户名已存在")
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	account := Account{Username: username, Password: string(hashed), Disabled: false}
	var granted []RoleDTO
	err = s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.repo.Create(txCtx, &account); err != nil {
			if database.IsDuplicateKey(err) {
				return errs.NewBizError("用户名已存在")
			}
			return err
		}
		granted, err = s.securitySvc.SetUserRoles(txCtx, account.ID, roleCodes)
		return err
	})
	if err != nil {
		return nil, err
	}
	dto := account.ToDTO()
	dto.Roles = granted
	return dto, nil
}

func (s *AuthenticateService) UpdateAccount(ctx context.Context, id uint, roles []RoleDTO, password *string) (*AuthorityUser, error) {
	if id == 0 {
		return nil, errs.NewBizError("id 不能为空")
	}
	curUser, err := GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, curUser.GetID(), AdminRoleCode)
	if err != nil {
		return nil, err
	}
	if !isAdmin && curUser.GetID() != id {
		return nil, errs.NewAuthError("无权限修改其他用户信息")
	}
	account, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errs.NewBizError("用户不存在")
	}
	currentRoles, err := s.securitySvc.RoleCodes(ctx, id)
	if err != nil {
		return nil, err
	}
	requestedRoles := roleCodes(roles)
	if roles == nil {
		requestedRoles = currentRoles
	}
	var granted []RoleDTO
	if !isAdmin && !sameStrings(requestedRoles, currentRoles) {
		return nil, errs.NewBizError("无权更改角色")
	}
	if isAdmin && roles != nil {
		if slices.Contains(currentRoles, AdminRoleCode) && !slices.Contains(requestedRoles, AdminRoleCode) {
			admins, err := s.FindEnabledByRole(ctx, AdminRoleCode)
			if err != nil {
				return nil, err
			}
			if len(admins) <= 1 {
				return nil, errs.NewBizError("必须保留至少一个启用的管理员")
			}
		}
	}
	var newPassword string
	if password != nil && *password != "" {
		if err := s.validPassword(password); err != nil {
			return nil, err
		}
		hashed, err := bcrypt.GenerateFromPassword([]byte(*password), bcrypt.DefaultCost)
		if err != nil {
			return nil, errs.Wrap(err)
		}
		newPassword = string(hashed)
	}
	// 角色与密码在同一事务内更新。
	err = s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if isAdmin && roles != nil {
			var txErr error
			granted, txErr = s.securitySvc.SetUserRoles(txCtx, id, requestedRoles)
			if txErr != nil {
				return txErr
			}
		}
		if newPassword != "" {
			account.Password = newPassword
			if txErr := s.repo.UpdatePassword(txCtx, account); txErr != nil {
				return txErr
			}
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	dto := account.ToDTO()
	if granted != nil {
		dto.Roles = granted
	} else {
		dto.Roles, err = s.securitySvc.UserRoles(ctx, id)
		if err != nil {
			return nil, err
		}
	}
	dto.Permissions, err = s.securitySvc.UserPermissions(ctx, id)
	if err != nil {
		return nil, err
	}
	return dto, nil
}

func (s *AuthenticateService) Disable(ctx context.Context, id uint) error {
	account, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return err
	}
	if account == nil {
		return errs.NewBizError("用户不存在")
	}
	curUser, err := GetCurrentUser(ctx)
	if err != nil {
		return err
	}
	if account.ID == curUser.GetID() {
		return errs.NewBizError("不能禁用自己")
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, account.ID, AdminRoleCode)
	if err != nil {
		return err
	}
	if isAdmin {
		admins, err := s.FindEnabledByRole(ctx, AdminRoleCode)
		if err != nil {
			return err
		}
		if len(admins) <= 1 {
			return errs.NewBizError("必须保留至少一个启用的管理员")
		}
	}
	disabled, err := s.repo.Disable(ctx, id)
	if err != nil {
		return err
	}
	if !disabled {
		return errs.NewBizError("用户不存在")
	}
	s.securitySvc.InvalidateAuthUser(id)
	return nil
}

// LoadUserById 读取完整 AuthorityUser；按 id 缓存账号、角色与权限，filter 直接使用。
func (s *AuthenticateService) LoadUserById(ctx context.Context, id uint) (*AuthorityUser, error) {
	key := fmt.Sprintf("%d", id)
	var user AuthorityUser
	if s.cache.GetJSON(authUserCacheName, key, &user) {
		return &user, nil
	}
	account, err := s.repo.FindByID(ctx, id)
	if err != nil || account == nil || account.Disabled {
		return nil, err
	}
	dto, err := s.toDTO(ctx, account)
	if err != nil {
		return nil, err
	}
	// 与 Java 的 WRITE_ONLY 密码字段一致：缓存中不保存密码哈希。
	cached := *dto
	cached.Password = ""
	s.cache.SetJSON(authUserCacheName, key, &cached, s.authUserTTL)
	return dto, nil
}

func (s *AuthenticateService) FindEnabledByID(ctx context.Context, id uint) (*Account, error) {
	account, err := s.repo.FindByID(ctx, id)
	if err != nil || account == nil || account.Disabled {
		return nil, err
	}
	return account, nil
}

func (s *AuthenticateService) FindByUsername(ctx context.Context, username string) (*AuthorityUser, error) {
	account, err := s.repo.FindByUsername(ctx, username)
	if err != nil || account == nil {
		return nil, err
	}
	return s.toDTO(ctx, account)
}

func (s *AuthenticateService) FindByID(ctx context.Context, id uint) (*AuthorityUser, error) {
	account, err := s.repo.FindByID(ctx, id)
	if err != nil || account == nil {
		return nil, err
	}
	return s.toDTO(ctx, account)
}

func (s *AuthenticateService) FindByIDs(ctx context.Context, ids []uint) ([]AuthorityUser, error) {
	accounts, err := s.repo.FindByIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	res := make([]AuthorityUser, len(accounts))
	for i := range accounts {
		res[i] = *accounts[i].ToDTO()
	}
	return res, nil
}

func (s *AuthenticateService) FindEnabledList(ctx context.Context) ([]AuthorityUser, error) {
	accounts, err := s.repo.FindEnabledList(ctx)
	if err != nil {
		return nil, err
	}
	res := make([]AuthorityUser, len(accounts))
	for i := range accounts {
		res[i] = *accounts[i].ToDTO()
	}
	return res, nil
}

func (s *AuthenticateService) FindEnabledPage(ctx context.Context, param *result.QueryParam) (*result.PageResult[AuthorityUser], error) {
	page, err := s.repo.FindEnabledPage(ctx, param)
	if err != nil {
		return nil, err
	}
	ids := make([]uint, len(page.Items))
	for i := range page.Items {
		ids[i] = page.Items[i].ID
	}
	rolesByUser, err := s.securitySvc.repo.FindRolesByUserIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	permissionsByUser, err := s.securitySvc.UserPermissionsByUserIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	dtos := make([]AuthorityUser, len(page.Items))
	for i := range page.Items {
		dtos[i] = *page.Items[i].ToDTO()
		dtos[i].Roles = roleDTOs(rolesByUser[page.Items[i].ID])
		dtos[i].Permissions = permissionsByUser[page.Items[i].ID]
	}
	return &result.PageResult[AuthorityUser]{
		Items:      dtos,
		TotalCount: page.TotalCount,
		PageIndex:  page.PageIndex,
		PageSize:   page.PageSize,
		TotalPages: page.TotalPages,
	}, nil
}

func (s *AuthenticateService) FindEnabledByRole(ctx context.Context, roleCode string) ([]AuthorityUser, error) {
	accounts, err := s.securitySvc.repo.FindAccountsByRole(ctx, roleCode)
	if err != nil {
		return nil, err
	}
	res := make([]AuthorityUser, len(accounts))
	for i, acc := range accounts {
		res[i] = *acc.ToDTO()
	}
	return res, nil
}

func (s *AuthenticateService) toDTO(ctx context.Context, account *Account) (*AuthorityUser, error) {
	dto := account.ToDTO()
	roles, err := s.securitySvc.UserRoles(ctx, account.ID)
	if err != nil {
		return nil, err
	}
	permissions, err := s.securitySvc.UserPermissions(ctx, account.ID)
	if err != nil {
		return nil, err
	}
	dto.Roles = roles
	dto.Permissions = permissions
	return dto, nil
}

func (s *AuthenticateService) assertCanAssignRoles(ctx context.Context, roles []string) error {
	curUser, err := GetCurrentUser(ctx)
	if err != nil {
		return err
	}
	isAdmin, err := s.securitySvc.HasRole(ctx, curUser.GetID(), AdminRoleCode)
	if err != nil {
		return err
	}
	if isAdmin {
		return nil
	}
	callerRoles, err := s.securitySvc.RoleCodes(ctx, curUser.GetID())
	if err != nil {
		return err
	}
	if !containsAll(callerRoles, roles) {
		return errs.NewBizError("无权授予未持有的角色")
	}
	return nil
}

func roleCodes(roles []RoleDTO) []string {
	codes := make([]string, 0, len(roles))
	for _, role := range roles {
		codes = append(codes, role.Code)
	}
	return codes
}

func roleDTOs(roles []Role) []RoleDTO {
	res := make([]RoleDTO, 0, len(roles))
	for _, role := range roles {
		res = append(res, *role.ToDTO())
	}
	return res
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

func (s *AuthenticateService) validUsername(username string) error {
	if !accountUsernamePattern.MatchString(username) {
		return errs.NewBizError("用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线")
	}
	return nil
}

func (s *AuthenticateService) validPassword(password *string) error {
	if password == nil || len(*password) < 10 {
		return errs.NewBizError("密码不能小于 10 位")
	}
	if len(*password) > 72 {
		return errs.NewBizError("密码不能大于 72 位")
	}
	if !accountPasswordPattern.MatchString(*password) {
		return errs.NewBizError("密码只能包含大写、小写字母、数字以及 *-_^~")
	}
	var lower, upper, digit, other bool
	for _, r := range *password {
		switch {
		case 'a' <= r && r <= 'z':
			lower = true
		case 'A' <= r && r <= 'Z':
			upper = true
		case '0' <= r && r <= '9':
			digit = true
		default:
			other = true
		}
	}
	classes := 0
	for _, present := range []bool{lower, upper, digit, other} {
		if present {
			classes++
		}
	}
	if classes < 2 {
		return errs.NewBizError("密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种")
	}
	return nil
}
