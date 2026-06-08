package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/audit"
	"backend-go/pkg/errs"
	"encoding/json"
	"time"

	"gorm.io/plugin/optimisticlock"
)

type State int

const (
	Created State = iota
	Processing
	Finished
)

func (s *State) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case Created:
		str = "CREATED"
	case Processing:
		str = "PROCESSING"
	case Finished:
		str = "FINISHED"

	}
	return json.Marshal(str)
}

func (s *State) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "CREATED":
			*s = Created
		case "PROCESSING":
			*s = Processing
		case "FINISHED":
			*s = Finished
		default:
			return errs.NewBizError("不支持的单据状态")
		}
	}
	return nil
}

// Reimbursement 摘要
type Reimbursement struct {
	audit.Audit
	ID     uint   `gorm:"primaryKey"`
	Number string `gorm:"uniqueIndex"`
	State  State
	// 摘要
	Summary     string
	TotalAmount float64
	// 报销时间
	ReimburseDate *time.Time `gorm:"type:timestamptz;index"`
	// 备注
	Remark  string
	Details []ReimburseDetail `gorm:"foreignKey:ReimbursementID"`
	Version optimisticlock.Version
}

// ReimburseDetail 摘要明细
type ReimburseDetail struct {
	ID              uint `gorm:"primaryKey"`
	ReimbursementID int  `gorm:"index"`
	Name            string
	Amount          float64
}

type DTO struct {
	ID            uint                   `json:"id"`
	Number        string                 `json:"number"`
	State         State                  `json:"state"`
	Summary       string                 `json:"summary"`
	TotalAmount   float64                `json:"totalAmount"`
	ReimburseDate *time.Time             `json:"reimburseDate"`
	Remark        string                 `json:"remark"`
	Details       []DetailDTO            `json:"details"`
	Attachments   []attach.AttachmentDTO `json:"attachments"`
}

type DetailDTO struct {
	ID     uint    `json:"id"`
	Name   string  `json:"name"`
	Amount float64 `json:"amount"`
}

// ToDTO 转换为详细 DTO
func (r *Reimbursement) ToDTO(attaches []attach.AttachmentDTO) *DTO {
	details := make([]DetailDTO, len(r.Details))
	for i, d := range r.Details {
		details[i] = *d.ToDTO()
	}
	base := r.ToBaseDTO()
	base.Details = details
	base.Attachments = attaches
	return base
}

// ToBaseDTO 转换为基础 DTO
func (r *Reimbursement) ToBaseDTO() *DTO {
	return &DTO{
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
func ToBaseDTOs(entities []Reimbursement) []DTO {
	details := make([]DTO, len(entities))
	for i, d := range entities {
		details[i] = *(d.ToBaseDTO())
	}
	return details
}

func (r *ReimburseDetail) ToDTO() *DetailDTO {
	return &DetailDTO{
		ID:     r.ID,
		Name:   r.Name,
		Amount: r.Amount,
	}
}

func (r *DTO) ToEntity() *Reimbursement {
	entity := Reimbursement{
		ID:            r.ID,
		Number:        r.Number,
		State:         r.State,
		Summary:       r.Summary,
		TotalAmount:   r.TotalAmount,
		ReimburseDate: r.ReimburseDate,
		Remark:        r.Remark,
	}
	details := make([]ReimburseDetail, len(r.Details))
	for i, d := range r.Details {
		details[i] = ReimburseDetail{
			ID:     d.ID,
			Name:   d.Name,
			Amount: d.Amount,
		}
	}
	entity.Details = details
	return &entity
}
