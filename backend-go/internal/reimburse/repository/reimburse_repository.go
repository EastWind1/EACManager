package repository

import (
	"backend-go/internal/reimburse/model"

	"gorm.io/gorm"
)

type ReimburseRepository struct {
	db *gorm.DB
}

func NewReimburseRepository(db *gorm.DB) *ReimburseRepository {
	return &ReimburseRepository{db: db}
}

func (r *ReimburseRepository) FindByID(id int) (*model.Reimbursement, error) {
	var bill model.Reimbursement
	err := r.db.Preload("Details").First(&bill, id).Error
	if err != nil {
		return nil, err
	}
	return &bill, nil
}

func (r *ReimburseRepository) FindByNumber(number string) (*model.Reimbursement, error) {
	var bill model.Reimbursement
	err := r.db.Where("number = ?", number).First(&bill).Error
	if err != nil {
		return nil, err
	}
	return &bill, nil
}

func (r *ReimburseRepository) ExistsByNumber(number string) (bool, error) {
	var count int64
	err := r.db.Model(&model.Reimbursement{}).Where("number = ?", number).Count(&count).Error
	return count > 0, err
}

func (r *ReimburseRepository) ExistsByID(id int) (bool, error) {
	var count int64
	err := r.db.Model(&model.Reimbursement{}).Where("id = ?", id).Count(&count).Error
	return count > 0, err
}

func (r *ReimburseRepository) FindAll() ([]*model.Reimbursement, error) {
	var bills []*model.Reimbursement
	err := r.db.Find(&bills).Error
	if err != nil {
		return nil, err
	}
	return bills, nil
}

func (r *ReimburseRepository) FindAllByIds(ids []int) ([]*model.Reimbursement, error) {
	var bills []*model.Reimbursement
	err := r.db.Where("id IN ?", ids).Find(&bills).Error
	if err != nil {
		return nil, err
	}
	return bills, nil
}

func (r *ReimburseRepository) Create(bill *model.Reimbursement) error {
	return r.db.Create(bill).Error
}

func (r *ReimburseRepository) Update(bill *model.Reimbursement) error {
	return r.db.Save(bill).Error
}

func (r *ReimburseRepository) Delete(id int) error {
	return r.db.Delete(&model.Reimbursement{}, id).Error
}

func (r *ReimburseRepository) FindByState(state model.ReimburseState) ([]*model.Reimbursement, error) {
	var bills []*model.Reimbursement
	err := r.db.Where("state = ?", state).Find(&bills).Error
	if err != nil {
		return nil, err
	}
	return bills, nil
}

func (r *ReimburseRepository) CountByState(state model.ReimburseState) (int64, error) {
	var count int64
	err := r.db.Model(&model.Reimbursement{}).Where("state = ?", state).Count(&count).Error
	return count, err
}
