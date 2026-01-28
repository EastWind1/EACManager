package model

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/common/audit"
	commonModel "backend-go/internal/common/result"
)

type ReimburseState string

const (
	ReimburseStateCreated    ReimburseState = "CREATED"
	ReimburseStateProcessing ReimburseState = "PROCESSING"
	ReimburseStateFinished   ReimburseState = "FINISHED"
)

type Reimbursement struct {
	audit.Entity
	ID            int               `json:"id" gorm:"primaryKey"`
	Number        string            `json:"number" gorm:"uniqueIndex;size:100"`
	State         ReimburseState    `json:"state" gorm:"size:50"`
	Summary       string            `json:"summary" gorm:"size:500"`
	TotalAmount   float64           `json:"totalAmount"`
	ReimburseDate *string           `json:"reimburseDate"`
	Remark        string            `json:"remark" gorm:"size:1000"`
	Details       []ReimburseDetail `json:"details" gorm:"foreignKey:ReimbursementID"`
	Version       int               `json:"version" gorm:"default:0"`
}

type ReimburseDetail struct {
	ID              int     `json:"id" gorm:"primaryKey"`
	ReimbursementID int     `json:"reimbursementId" gorm:"index"`
	Name            string  `json:"name" gorm:"size:200"`
	Amount          float64 `json:"amount"`
}

type ReimbursementDTO struct {
	ID            int                   `json:"id"`
	Number        string                `json:"number"`
	State         ReimburseState        `json:"state"`
	Summary       string                `json:"summary"`
	TotalAmount   float64               `json:"totalAmount"`
	ReimburseDate *string               `json:"reimburseDate"`
	Remark        string                `json:"remark"`
	Details       []ReimburseDetailDTO  `json:"details"`
	Attachments   []model.AttachmentDTO `json:"attachments"`
}

type ReimburseDetailDTO struct {
	ID     int     `json:"id"`
	Name   string  `json:"name"`
	Amount float64 `json:"amount"`
}

type ReimburseQueryParam struct {
	Number             string           `form:"number"`
	Summary            string           `form:"summary"`
	States             []ReimburseState `form:"states"`
	ReimburseStartDate *string          `form:"reimburseStartDate"`
	ReimburseEndDate   *string          `form:"reimburseEndDate"`
	commonModel.QueryParam
	commonModel.SortParam
}

func (r *Reimbursement) ToDTO() ReimbursementDTO {
	details := make([]ReimburseDetailDTO, len(r.Details))
	for i, d := range r.Details {
		details[i] = d.ToDTO()
	}

	return ReimbursementDTO{
		ID:            r.ID,
		Number:        r.Number,
		State:         r.State,
		Summary:       r.Summary,
		TotalAmount:   r.TotalAmount,
		ReimburseDate: r.ReimburseDate,
		Remark:        r.Remark,
		Details:       details,
	}
}

func (r *Reimbursement) ToBaseDTO() ReimbursementDTO {
	details := make([]ReimburseDetailDTO, len(r.Details))
	for i, d := range r.Details {
		details[i] = d.ToDTO()
	}

	return ReimbursementDTO{
		ID:            r.ID,
		Number:        r.Number,
		State:         r.State,
		Summary:       r.Summary,
		TotalAmount:   r.TotalAmount,
		ReimburseDate: r.ReimburseDate,
		Details:       details,
	}
}

func (r *ReimburseDetail) ToDTO() ReimburseDetailDTO {
	return ReimburseDetailDTO{
		ID:     r.ID,
		Name:   r.Name,
		Amount: r.Amount,
	}
}
