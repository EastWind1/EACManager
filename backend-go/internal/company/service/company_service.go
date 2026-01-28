package service

import (
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/company/model"
	"backend-go/internal/company/repository"
	"context"
)

type CompanyService struct {
	companyRepo *repository.CompanyRepository
}

func NewCompanyService(companyRepo *repository.CompanyRepository) *CompanyService {
	return &CompanyService{
		companyRepo: companyRepo,
	}
}

func (s *CompanyService) FindEnabled(ctx context.Context, param *result.QueryParam) (*result.PageResult[model.CompanyDTO], error) {
	companies, err := s.companyRepo.FindAllEnabled(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewPageResultFromDB(companies, model.ToDTOs), nil
}

func (s *CompanyService) FindByName(ctx context.Context, name string) ([]model.CompanyDTO, error) {
	companies, err := s.companyRepo.FindEnabledByNameContains(ctx, name)
	if err != nil {
		return nil, err
	}
	res := make([]model.CompanyDTO, len(*companies))
	for i, c := range *companies {
		res[i] = *c.ToDTO()
	}
	return res, nil
}

func (s *CompanyService) Create(ctx context.Context, dto *model.CompanyDTO) (*model.CompanyDTO, error) {
	if dto.Name == "" {
		return nil, errs.NewBizError("公司名称不能为空")
	}
	company := dto.ToEntity()
	if err := s.companyRepo.Create(ctx, company); err != nil {
		return nil, err
	}
	return company.ToDTO(), nil
}

func (s *CompanyService) Update(ctx context.Context, dto *model.CompanyDTO) (*model.CompanyDTO, error) {
	var res *model.CompanyDTO
	err := s.companyRepo.WithTransaction(func(r *repository.CompanyRepository) error {
		com, err := r.FindByID(ctx, dto.ID)
		if err != nil {
			return err
		}
		if com == nil {
			return errs.NewBizError("公司不存在")
		}
		company := dto.ToEntity()
		if err = r.Save(ctx, company); err != nil {
			return err
		}
		res = company.ToDTO()
		return nil
	})
	if err != nil {
		return nil, err
	}
	return res, nil
}

func (s *CompanyService) Disable(ctx context.Context, id int) error {
	err := s.companyRepo.WithTransaction(func(r *repository.CompanyRepository) error {
		company, err := r.FindByID(ctx, id)
		if err != nil {
			return err
		}
		if company == nil {
			return errs.NewBizError("公司不存在")
		}
		company.IsDisabled = true
		return r.Save(ctx, company)
	})
	return err
}
