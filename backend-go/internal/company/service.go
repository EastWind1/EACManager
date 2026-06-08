package company

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
)

type Service struct {
	companyRepo *Repository
}

func NewCompanyService(companyRepo *Repository) *Service {
	return &Service{
		companyRepo: companyRepo,
	}
}

func (s *Service) FindEnabled(ctx context.Context, param *result.QueryParam) (*result.PageResult[DTO], error) {
	companies, err := s.companyRepo.FindAllEnabled(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewDTOPageResult(companies, ToDTOs), nil
}

func (s *Service) FindByName(ctx context.Context, name string) ([]DTO, error) {
	companies, err := s.companyRepo.FindEnabledByNameContains(ctx, name)
	if err != nil {
		return nil, err
	}
	res := make([]DTO, len(companies))
	for i, c := range companies {
		res[i] = *c.ToDTO()
	}
	return res, nil
}

func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.Name == "" {
		return nil, errs.NewBizError("公司名称不能为空")
	}
	company := dto.ToEntity()
	if err := s.companyRepo.Create(ctx, company); err != nil {
		return nil, err
	}
	return company.ToDTO(), nil
}

func (s *Service) Update(ctx context.Context, dto *DTO) (*DTO, error) {
	var company *Company
	err := s.companyRepo.Transaction(ctx, func(tx context.Context) error {
		com, err := s.companyRepo.FindByID(tx, dto.ID)
		if err != nil {
			return err
		}
		if com == nil {
			return errs.NewBizError("公司不存在")
		}
		company = dto.ToEntity()
		if err = s.companyRepo.Updates(tx, company); err != nil {
			return err
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return company.ToDTO(), nil
}

func (s *Service) Disable(ctx context.Context, id uint) error {
	err := s.companyRepo.Transaction(ctx, func(tx context.Context) error {
		company, err := s.companyRepo.FindByID(tx, id)
		if err != nil {
			return err
		}
		if company == nil {
			return errs.NewBizError("公司不存在")
		}
		company.Disabled = true
		return s.companyRepo.Updates(tx, company)
	})
	return err
}
