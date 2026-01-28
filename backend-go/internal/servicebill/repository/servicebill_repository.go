package repository

import (
	"backend-go/internal/servicebill/model"

	"gorm.io/gorm"
)

type ServiceBillRepository struct {
	db *gorm.DB
}

func NewServiceBillRepository(db *gorm.DB) *ServiceBillRepository {
	return &ServiceBillRepository{db: db}
}

func (r *ServiceBillRepository) FindByID(id int) (*model.ServiceBill, error) {
	var bill model.ServiceBill
	err := r.db.Preload("Details").Preload("ProductCompany").First(&bill, id).Error
	if err != nil {
		return nil, err
	}
	return &bill, nil
}

func (r *ServiceBillRepository) FindByNumber(number string) (*model.ServiceBill, error) {
	var bill model.ServiceBill
	err := r.db.Where("number = ?", number).First(&bill).Error
	if err != nil {
		return nil, err
	}
	return &bill, nil
}

func (r *ServiceBillRepository) ExistsByNumber(number string) (bool, error) {
	var count int64
	err := r.db.Model(&model.ServiceBill{}).Where("number = ?", number).Count(&count).Error
	return count > 0, err
}

func (r *ServiceBillRepository) ExistsByID(id int) (bool, error) {
	var count int64
	err := r.db.Model(&model.ServiceBill{}).Where("id = ?", id).Count(&count).Error
	return count > 0, err
}

func (r *ServiceBillRepository) Create(bill *model.ServiceBill) error {
	return r.db.Create(bill).Error
}

func (r *ServiceBillRepository) Update(bill *model.ServiceBill) error {
	return r.db.Save(bill).Error
}

func (r *ServiceBillRepository) Delete(id int) error {
	return r.db.Delete(&model.ServiceBill{}, id).Error
}

func (r *ServiceBillRepository) FindByState(state model.ServiceBillState) ([]*model.ServiceBill, error) {
	var bills []*model.ServiceBill
	err := r.db.Where("state = ?", state).Find(&bills).Error
	if err != nil {
		return nil, err
	}
	return bills, nil
}

func (r *ServiceBillRepository) CountByState(state model.ServiceBillState) (int64, error) {
	var count int64
	err := r.db.Model(&model.ServiceBill{}).Where("state = ?", state).Count(&count).Error
	return count, err
}

func (r *ServiceBillRepository) FindAll() ([]*model.ServiceBill, error) {
	var bills []*model.ServiceBill
	err := r.db.Find(&bills).Error
	if err != nil {
		return nil, err
	}
	return bills, nil
}
