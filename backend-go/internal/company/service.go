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
	if dto.ID == 0 {
		return nil, errs.NewBizError("公司 ID 不能为空")
	}
	company := dto.ToEntity()
	// 单条 UPDATE ... WHERE id = ?：受影响行数为 0 时 Update 返回「数据已被更改」，
	// 不必先 select 一次再写
	if err := s.companyRepo.Update(ctx, company); err != nil {
		return nil, err
	}
	return company.ToDTO(), nil
}

func (s *Service) Disable(ctx context.Context, id uint) error {
	if id == 0 {
		return errs.NewBizError("公司 ID 不能为空")
	}
	return s.companyRepo.SetDisabled(ctx, id)
}
