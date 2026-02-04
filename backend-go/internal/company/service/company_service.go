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

func (s *CompanyService) FindEnabled(ctx context.Context, param *result.QueryParam) (*result.PageResult[model.CompanyDTO], errs.StackError) {
	companies, err := s.companyRepo.FindAllEnabled(ctx, param)
	if err != nil {
		return nil, err
	}

	return result.NewPageResultFromDB(companies, model.ToDTOs), nil
}

func (s *CompanyService) FindByName(ctx context.Context, name string) ([]model.CompanyDTO, errs.StackError) {
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

func (s *CompanyService) Create(ctx context.Context, dto *model.CompanyDTO) (*model.CompanyDTO, errs.StackError) {
	if dto.Name == "" {
		return nil, errs.NewBizError("公司名称不能为空")
	}
	company := dto.ToEntity()
	if err := s.companyRepo.Create(ctx, company); err != nil {
		return nil, err
	}
	return company.ToDTO(), nil
}

func (s *CompanyService) Update(ctx context.Context, dto *model.CompanyDTO) (*model.CompanyDTO, errs.StackError) {
	var company *model.Company
	err := s.companyRepo.Transaction(ctx, func(tx context.Context) errs.StackError {
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

func (s *CompanyService) Disable(ctx context.Context, id int) errs.StackError {
	err := s.companyRepo.Transaction(ctx, func(tx context.Context) errs.StackError {
		company, err := s.companyRepo.FindByID(tx, id)
		if err != nil {
			return err
		}
		if company == nil {
			return errs.NewBizError("公司不存在")
		}
		company.IsDisabled = true
		return s.companyRepo.Updates(tx, company)
	})
	return err
}
