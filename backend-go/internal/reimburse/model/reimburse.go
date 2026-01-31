package model

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/common/audit"
	"time"
)

type ReimburseState int

const (
	ReimburseStateCreated ReimburseState = iota
	ReimburseStateProcessing
	ReimburseStateFinished
)

// Reimbursement 摘要
type Reimbursement struct {
	audit.Entity
	ID     uint           `gorm:"primaryKey;default:nextval('reimbursement_seq')"`
	Number string         `gorm:"uniqueIndex"`
	State  ReimburseState `gorm:"default:0"`
	// 摘要
	Summary     string
	TotalAmount float64
	// 报销时间
	ReimburseDate *time.Time `gorm:"type:timestamptz;index"`
	// 备注
	Remark  string
	Details []ReimburseDetail `gorm:"foreignKey:ReimbursementID"`
	Version int
}

// ReimburseDetail 摘要明细
type ReimburseDetail struct {
	ID              uint `gorm:"primaryKey;default:nextval('reimburse_detail_seq')"`
	ReimbursementID int  `gorm:"index"`
	Name            string
	Amount          float64
}

type ReimbursementDTO struct {
	ID            uint                  `json:"id"`
	Number        string                `json:"number"`
	State         ReimburseState        `json:"state"`
	Summary       string                `json:"summary"`
	TotalAmount   float64               `json:"totalAmount"`
	ReimburseDate *time.Time            `json:"reimburseDate"`
	Remark        string                `json:"remark"`
	Details       []ReimburseDetailDTO  `json:"details"`
	Attachments   []model.AttachmentDTO `json:"attachments"`
}

type ReimburseDetailDTO struct {
	ID     uint    `json:"id"`
	Name   string  `json:"name"`
	Amount float64 `json:"amount"`
}

// ToDTO 转换为详细 DTO
func (r *Reimbursement) ToDTO(attaches *[]model.AttachmentDTO) *ReimbursementDTO {
	details := make([]ReimburseDetailDTO, len(r.Details))
	for i, d := range r.Details {
		details[i] = d.ToDTO()
	}
	base := r.ToBaseDTO()
	base.Details = details
	base.Attachments = *attaches
	return base
}

// ToBaseDTO 转换为基础 DTO
func (r *Reimbursement) ToBaseDTO() *ReimbursementDTO {
	return &ReimbursementDTO{
		ID:            r.ID,
		Number:        r.Number,
		State:         r.State,
		Summary:       r.Summary,
		TotalAmount:   r.TotalAmount,
		ReimburseDate: r.ReimburseDate,
		Remark:        r.Remark,
	}
}

// ToBaseDTOs 转换为基础 DTO，用于列表展示
func ToBaseDTOs(entities *[]Reimbursement) *[]ReimbursementDTO {
	details := make([]ReimbursementDTO, len(*entities))
	for i, d := range *entities {
		details[i] = *(d.ToBaseDTO())
	}
	return &details
}

func (r *ReimburseDetail) ToDTO() ReimburseDetailDTO {
	return ReimburseDetailDTO{
		ID:     r.ID,
		Name:   r.Name,
		Amount: r.Amount,
	}
}
