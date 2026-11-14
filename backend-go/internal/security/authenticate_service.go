package security

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"regexp"
	"slices"
	"strings"

	"github.com/gofiber/fiber/v3/log"
	"golang.org/x/crypto/bcrypt"
)

var (
	accountUsernamePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]{3,31}$`)
	accountPasswordPattern = regexp.MustCompile(`^[a-zA-Z0-9*\-_^~]+$`)
)

type AuthenticateService struct {
	repo        *AccountRepository
	jwtSrv      *JWTService
	securitySvc *AuthorizeService
}

func newAuthenticateService(jwt *JWTService, repo *AccountRepository, securitySvc *AuthorizeService) *AuthenticateService {
	return &AuthenticateService{
		repo:        repo,
		jwtSrv:      jwt,
		securitySvc: securitySvc,
	}
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
	token, err := s.jwtSrv.GenerateToken(username)
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
	hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	account := Account{Username: username, Password: string(hashed), Disabled: false}
	var granted []RoleDTO
	err = s.repo.Transaction(ctx, func(txCtx context.Context) error {
		if err := s.repo.Create(txCtx, &account); err != nil {
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
		if granted, err = s.securitySvc.SetUserRoles(ctx, id, requestedRoles); err != nil {
			return nil, err
		}
	}
	if password != nil && *password != "" {
		if err := s.validPassword(password); err != nil {
			return nil, err
		}
		hashed, err := bcrypt.GenerateFromPassword([]byte(*password), bcrypt.DefaultCost)
		if err != nil {
			return nil, errs.Wrap(err)
		}
		account.Password = string(hashed)
		if err = s.repo.UpdatePassword(ctx, account); err != nil {
			return nil, err
		}
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

func (s *AuthenticateService) Disable(ctx context.Context, username string) error {
	account, err := s.repo.FindByUsername(ctx, username)
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
	disabled, err := s.repo.Disable(ctx, username)
	if err != nil {
		return err
	}
	if !disabled {
		return errs.NewBizError("用户不存在")
	}
	return nil
}

func (s *AuthenticateService) FindEnabledByUsername(ctx context.Context, username string) (*Account, error) {
	return s.repo.FindEnabledByUsername(ctx, username)
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
	dtos := make([]AuthorityUser, len(page.Items))
	for i := range page.Items {
		dtos[i] = *page.Items[i].ToDTO()
		dtos[i].Roles = roleDTOs(rolesByUser[page.Items[i].ID])
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
	lc, uc, nc, oc := 0, 0, 0, 0
	for _, r := range *password {
		switch {
		case 'a' <= r && r <= 'z':
			lc++
		case 'A' <= r && r <= 'Z':
			uc++
		case '0' <= r && r <= '9':
			nc++
		default:
			oc++
		}
	}
	if lc+uc+nc+oc < 2 {
		return errs.NewBizError("密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种")
	}
	return nil
}
