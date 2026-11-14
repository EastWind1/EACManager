package user

import (
	"backend-go/internal/security"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"regexp"
	"strings"
)

var (
	phonePattern = regexp.MustCompile(`^1[3-9]\d{9}$`)
	emailPattern = regexp.MustCompile(`^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$`)
)

type Service struct {
	profileRepo *Repository
	security    *security.Module
}

func newService(profileRepo *Repository, security *security.Module) *Service {
	return &Service{profileRepo: profileRepo, security: security}
}

func (s *Service) IsInitializationRequired(ctx context.Context) (bool, error) {
	return s.security.Authenticate.IsInitializationRequired(ctx)
}

func (s *Service) Login(ctx context.Context, username, password string) (*LoginResult, error) {
	res, err := s.security.Authenticate.Login(ctx, username, password)
	if err != nil {
		return nil, err
	}
	profile, err := s.profileRepo.FindByUserID(ctx, res.Account.ID)
	if err != nil {
		return nil, err
	}
	return &LoginResult{Token: res.Token, User: *toDTO(&res.Account, profile)}, nil
}

func (s *Service) Initialize(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.Password == "" {
		return nil, errs.NewBizError("密码不能为空")
	}
	if err := s.validateProfile(dto.Name, dto.Phone, dto.Email); err != nil {
		return nil, err
	}
	var res *DTO
	err := s.profileRepo.Transaction(ctx, func(txCtx context.Context) error {
		account, err := s.security.Authenticate.Initialize(txCtx, dto.Username, dto.Password)
		if err != nil {
			return err
		}
		profile := UserProfile{
			UserID: account.ID,
			Name:   strings.TrimSpace(dto.Name),
			Phone:  strings.TrimSpace(dto.Phone),
			Email:  strings.TrimSpace(dto.Email),
		}
		if err = s.profileRepo.Create(txCtx, &profile); err != nil {
			return err
		}
		res = toDTO(account, &profile)
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *Service) GetAll(ctx context.Context, queryParam *result.QueryParam) (*result.PageResult[DTO], error) {
	curUser, err := security.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	isAdmin, err := s.security.Authorize.HasRole(ctx, curUser.GetID(), security.AdminRoleCode)
	if err != nil {
		return nil, err
	}
	if !isAdmin {
		account, err := s.security.Authenticate.FindByID(ctx, curUser.GetID())
		if err != nil {
			return nil, err
		}
		if account == nil {
			return result.NewPageResult([]DTO{}, 0, 0, 1), nil
		}
		profile, err := s.profileRepo.FindByUserID(ctx, account.ID)
		if err != nil {
			return nil, err
		}
		return result.NewPageResult([]DTO{*toDTO(account, profile)}, 1, 0, 1), nil
	}
	page, err := s.security.Authenticate.FindEnabledPage(ctx, queryParam)
	if err != nil {
		return nil, err
	}
	profiles, err := s.profileMap(ctx, accountIDs(page.Items))
	if err != nil {
		return nil, err
	}
	dtos := make([]DTO, len(page.Items))
	for i := range page.Items {
		dtos[i] = *toDTO(&page.Items[i], profiles[page.Items[i].ID])
	}
	return &result.PageResult[DTO]{
		Items:      dtos,
		TotalCount: page.TotalCount,
		PageIndex:  page.PageIndex,
		PageSize:   page.PageSize,
		TotalPages: page.TotalPages,
	}, nil
}

func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.Password == "" {
		return nil, errs.NewBizError("密码不能为空")
	}
	if err := s.validateProfile(dto.Name, dto.Phone, dto.Email); err != nil {
		return nil, err
	}
	var res *DTO
	err := s.profileRepo.Transaction(ctx, func(txCtx context.Context) error {
		account, err := s.security.Authenticate.CreateAccount(txCtx, dto.Username, dto.Password, dto.Roles)
		if err != nil {
			return err
		}
		profile := UserProfile{
			UserID: account.ID,
			Name:   strings.TrimSpace(dto.Name),
			Phone:  strings.TrimSpace(dto.Phone),
			Email:  strings.TrimSpace(dto.Email),
		}
		if err = s.profileRepo.Create(txCtx, &profile); err != nil {
			return err
		}
		res = toDTO(account, &profile)
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
	existing, err := s.security.Authenticate.FindByID(ctx, dto.ID)
	if err != nil {
		return nil, err
	}
	if existing == nil {
		return nil, errs.NewBizError("用户不存在")
	}
	if existing.Username != dto.Username {
		return nil, errs.NewBizError("用户名不能修改")
	}
	if err := s.validateProfile(dto.Name, dto.Phone, dto.Email); err != nil {
		return nil, err
	}
	var res *DTO
	err = s.profileRepo.Transaction(ctx, func(txCtx context.Context) error {
		var password *string
		if dto.Password != "" {
			password = &dto.Password
		}
		account, err := s.security.Authenticate.UpdateAccount(txCtx, dto.ID, dto.Roles, password)
		if err != nil {
			return err
		}
		profile, err := s.profileRepo.FindByUserID(txCtx, dto.ID)
		if err != nil {
			return err
		}
		if profile == nil {
			profile = &UserProfile{UserID: dto.ID}
			profile.Name = strings.TrimSpace(dto.Name)
			profile.Phone = strings.TrimSpace(dto.Phone)
			profile.Email = strings.TrimSpace(dto.Email)
			if err = s.profileRepo.Create(txCtx, profile); err != nil {
				return err
			}
		} else {
			profile.Name = strings.TrimSpace(dto.Name)
			profile.Phone = strings.TrimSpace(dto.Phone)
			profile.Email = strings.TrimSpace(dto.Email)
			if err = s.profileRepo.Update(txCtx, profile); err != nil {
				return err
			}
		}
		res = toDTO(account, profile)
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *Service) FindEnabledUsersByRole(ctx context.Context, roleCode string) ([]DTO, error) {
	accounts, err := s.security.Authenticate.FindEnabledByRole(ctx, roleCode)
	if err != nil {
		return nil, err
	}
	return s.withProfiles(ctx, accounts)
}

func (s *Service) FindEnabledUsers(ctx context.Context) ([]DTO, error) {
	accounts, err := s.security.Authenticate.FindEnabledList(ctx)
	if err != nil {
		return nil, err
	}
	return s.withProfiles(ctx, accounts)
}

func (s *Service) FindByIDs(ctx context.Context, ids []uint) ([]DTO, error) {
	accounts, err := s.security.Authenticate.FindByIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	return s.withProfiles(ctx, accounts)
}

func (s *Service) FindEnabledByID(ctx context.Context, id uint) (*DTO, error) {
	account, err := s.security.Authenticate.FindByID(ctx, id)
	if err != nil || account == nil || account.Disabled {
		return nil, err
	}
	profile, err := s.profileRepo.FindByUserID(ctx, id)
	if err != nil {
		return nil, err
	}
	return toDTO(account, profile), nil
}

func (s *Service) withProfiles(ctx context.Context, accounts []security.AuthorityUser) ([]DTO, error) {
	profiles, err := s.profileMap(ctx, accountIDs(accounts))
	if err != nil {
		return nil, err
	}
	res := make([]DTO, len(accounts))
	for i := range accounts {
		res[i] = *toDTO(&accounts[i], profiles[accounts[i].ID])
	}
	return res, nil
}

func (s *Service) profileMap(ctx context.Context, ids []uint) (map[uint]*UserProfile, error) {
	profiles, err := s.profileRepo.FindByUserIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	res := make(map[uint]*UserProfile, len(profiles))
	for i := range profiles {
		res[profiles[i].UserID] = &profiles[i]
	}
	return res, nil
}

func accountIDs(accounts []security.AuthorityUser) []uint {
	ids := make([]uint, len(accounts))
	for i := range accounts {
		ids[i] = accounts[i].ID
	}
	return ids
}

func toDTO(account *security.AuthorityUser, profile *UserProfile) *DTO {
	dto := &DTO{AuthorityUser: *account}
	dto.Password = ""
	if profile != nil {
		dto.Name = profile.Name
		dto.Phone = profile.Phone
		dto.Email = profile.Email
	}
	return dto
}

func (s *Service) validateProfile(name, phone, email string) error {
	name = strings.TrimSpace(name)
	if name == "" || len([]rune(name)) > 64 {
		return errs.NewBizError("姓名不能为空且不能超过 64 个字符")
	}
	phone = strings.TrimSpace(phone)
	if phone != "" && !phonePattern.MatchString(phone) {
		return errs.NewBizError("手机号格式不正确")
	}
	email = strings.TrimSpace(email)
	if email != "" && (len(email) > 128 || !emailPattern.MatchString(email)) {
		return errs.NewBizError("邮箱格式不正确")
	}
	return nil
}
