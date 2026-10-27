package bill

import (
	"backend-go/internal/company"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"
	"fmt"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// Repository 基于 GORM 实现的 Repository，用于 ServiceBill 的数据访问
type Repository struct {
	*database.BaseRepository[ServiceBill]
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{
		BaseRepository: database.NewBaseRepository[ServiceBill](db),
	}
}

// Insert 新建单据，明细与主表同一条语句写入（子表持外键，无需回填）
func (r *Repository) Insert(ctx context.Context, entity *ServiceBill) error {
	if err := gorm.G[ServiceBill](r.Ctx(ctx)).Create(ctx, entity); err != nil {
		if database.IsDuplicateKey(err) {
			return errs.NewBizError("单据编号已存在")
		}
		return errs.Wrap(err)
	}
	return nil
}

// FindByID 查询单据主表（不含明细与公司），状态流转只需要主表字段
func (r *Repository) FindByID(ctx context.Context, id uint) (*ServiceBill, error) {
	record, err := gorm.G[ServiceBill](r.Ctx(ctx)).
		Select("id", "number", "type", "state", "total_amount", "processed_date", "finished_date", "version").
		Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &record, nil
}

// FindFullById 预加载 Details 与 ProductCompany，避免 N+1。
func (r *Repository) FindFullById(ctx context.Context, id uint) (*ServiceBill, error) {
	record, err := gorm.G[ServiceBill](r.Ctx(ctx)).
		Preload("Details", nil).
		Preload("ProductCompany", nil).
		Where("id = ?", id).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, errs.Wrap(err)
	}
	return &record, nil
}

// FindAllWithDetails 批量查询完整实体（导出）
//
// 固定 3 条语句，与单据数量无关：主表、明细、公司各一次，明细与公司都按主键 IN 批量取回，
// 避免 Preload 的「先查单据再查明细」退化成每张单据一次查询。
func (r *Repository) FindAllWithDetails(ctx context.Context, ids []uint) ([]ServiceBill, error) {
	if len(ids) == 0 {
		return []ServiceBill{}, nil
	}
	bills, err := gorm.G[ServiceBill](r.Ctx(ctx)).Where("id IN ?", ids).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	if len(bills) == 0 {
		return bills, nil
	}

	billIDs := make([]uint, 0, len(bills))
	companyIDs := make([]uint, 0, len(bills))
	for _, bill := range bills {
		billIDs = append(billIDs, bill.ID)
		if bill.ProductCompanyID != nil {
			companyIDs = append(companyIDs, *bill.ProductCompanyID)
		}
	}

	detailsByBill, err := r.findDetailsByBillIDs(ctx, billIDs)
	if err != nil {
		return nil, err
	}
	companiesByID, err := r.findCompaniesByIDs(ctx, companyIDs)
	if err != nil {
		return nil, err
	}

	for i := range bills {
		bills[i].Details = detailsByBill[bills[i].ID]
		if bills[i].ProductCompanyID != nil {
			bills[i].ProductCompany = companiesByID[*bills[i].ProductCompanyID]
		}
	}
	return bills, nil
}

func (r *Repository) findDetailsByBillIDs(ctx context.Context, billIDs []uint) (map[uint][]Detail, error) {
	details, err := gorm.G[Detail](r.Ctx(ctx)).Where("service_bill_id IN ?", billIDs).Order("id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	res := make(map[uint][]Detail, len(billIDs))
	for _, detail := range details {
		billID := uint(detail.ServiceBillID)
		res[billID] = append(res[billID], detail)
	}
	return res, nil
}

func (r *Repository) findCompaniesByIDs(ctx context.Context, ids []uint) (map[uint]*company.Company, error) {
	res := make(map[uint]*company.Company, len(ids))
	if len(ids) == 0 {
		return res, nil
	}
	companies, err := gorm.G[company.Company](r.Ctx(ctx)).Where("id IN ?", ids).Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	for i := range companies {
		res[companies[i].ID] = &companies[i]
	}
	return res, nil
}

// FindByParam 只负责拼查询链；计数、排序与分页由 BaseRepository.Page 统一处理。
func (r *Repository) FindByParam(ctx context.Context, param *QueryParam) (*result.PageResult[ServiceBill], error) {
	// 条件全部通过泛型链添加：gorm.G 会重置语句，挂在 *gorm.DB 上的条件不会生效
	g := r.Chain(ctx)
	if param.Number != "" {
		g = g.Where("number = ?", param.Number)
	}
	if len(param.States) > 0 {
		g = g.Where("state IN ?", param.States)
	}
	if param.ProjectName != "" {
		g = g.Where("project_name LIKE ?", fmt.Sprintf("%%%s%%", param.ProjectName))
	}
	if param.OrderStartDate != nil {
		g = g.Where("order_date >= ?", param.OrderStartDate)
	}
	if param.OrderEndDate != nil {
		g = g.Where("order_date <= ?", param.OrderEndDate)
	}
	if param.ProcessedStartDate != nil {
		g = g.Where("processed_date >= ?", param.ProcessedStartDate)
	}
	if param.ProcessedEndDate != nil {
		g = g.Where("processed_date <= ?", param.ProcessedEndDate)
	}

	// 列表要展示公司名：预加载由本仓储直接调 GORM API 加上，链本身原样交给 Page
	return r.Page(ctx, g.Preload("ProductCompany", nil), &param.QueryParam)
}

// Updates 更新主表字段并同步明细
func (r *Repository) Updates(ctx context.Context, entity *ServiceBill) error {
	affected, err := gorm.G[*ServiceBill](r.Ctx(ctx)).Select(
		"number", "type", "state", "total_amount", "project_name", "project_address",
		"project_contact", "project_contact_phone", "on_site_contact", "on_site_phone",
		"elevator_info", "order_date", "processed_date", "finished_date", "remark").
		Where("id = ?", entity.ID).Updates(ctx, entity)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return r.syncDetails(ctx, entity)
}

// UpdateState 只更新状态字段；调用方已确认目标状态，这里不做回写
func (r *Repository) UpdateState(ctx context.Context, entity *ServiceBill) error {
	affected, err := gorm.G[*ServiceBill](r.Ctx(ctx)).Select(
		"state", "processed_date", "finished_date").
		Where("id = ?", entity.ID).Updates(ctx, entity)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// syncDetails 明细对账：删除孤儿、原地更新保留行、新增缺失行
func (r *Repository) syncDetails(ctx context.Context, entity *ServiceBill) error {
	existing, err := gorm.G[Detail](r.Ctx(ctx)).
		Select("id").Where("service_bill_id = ?", entity.ID).Find(ctx)
	if err != nil {
		return errs.Wrap(err)
	}
	keep := make(map[uint]bool, len(entity.Details))
	toUpdate := make([]Detail, 0, len(entity.Details))
	toInsert := make([]Detail, 0, len(entity.Details))
	for _, detail := range entity.Details {
		detail.ServiceBillID = int(entity.ID)
		if detail.ID == 0 {
			toInsert = append(toInsert, detail)
			continue
		}
		keep[detail.ID] = true
		toUpdate = append(toUpdate, detail)
	}

	remove := make([]uint, 0, len(existing))
	for _, detail := range existing {
		if !keep[detail.ID] {
			remove = append(remove, detail.ID)
		}
	}
	if len(remove) > 0 {
		if _, err = gorm.G[Detail](r.Ctx(ctx)).Where("id IN ?", remove).Delete(ctx); err != nil {
			return errs.Wrap(err)
		}
	}
	// 批量原地更新：一条 INSERT ... ON CONFLICT (id) DO UPDATE，而不是逐行 UPDATE
	if len(toUpdate) > 0 {
		err = gorm.G[Detail](r.Ctx(ctx), clause.OnConflict{
			Columns: []clause.Column{{Name: "id"}},
			DoUpdates: clause.AssignmentColumns(
				[]string{"service_bill_id", "device", "quantity", "unit_price", "subtotal", "remark"}),
		}).CreateInBatches(ctx, &toUpdate, relationBatchSize)
		if err != nil {
			return errs.Wrap(err)
		}
	}
	if len(toInsert) > 0 {
		if err = gorm.G[Detail](r.Ctx(ctx)).CreateInBatches(ctx, &toInsert, relationBatchSize); err != nil {
			return errs.Wrap(err)
		}
	}
	entity.Details = append(toUpdate, toInsert...)
	return nil
}

func (r *Repository) Delete(ctx context.Context, id uint) (bool, error) {
	affected, err := gorm.G[*ServiceBill](r.Ctx(ctx)).Select("Details").Where("id = ?", id).Delete(ctx)
	if err != nil {
		return false, errs.Wrap(err)
	}
	return affected > 0, nil
}
