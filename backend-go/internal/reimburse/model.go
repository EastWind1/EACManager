package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/internal/security"
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

func (s *State) String() string {
	return []string{"CREATED", "PROCESSING", "FINISHED"}[*s]
}

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

// Reimburse 类名已简化，表名仍为 reimbursement，故显式指定。
type Reimburse struct {
	security.Audit
	ID     uint   `gorm:"primaryKey"`
	Number string `gorm:"uniqueIndex"`
	State  State

	Summary     string
	TotalAmount float64

	ReimburseDate *time.Time `gorm:"type:timestamptz;index"`

	Remark  string
	Details []Detail `gorm:"foreignKey:ReimbursementID"`
	Version optimisticlock.Version
}

func (r *Reimburse) TableName() string {
	return "reimbursement"
}

type Detail struct {
	ID              uint `gorm:"primaryKey"`
	ReimbursementID int  `gorm:"index"`
	Name            string
	Amount          float64
}

func (d *Detail) TableName() string {
	return "reimburse_detail"
}

type DTO struct {
	ID            uint         `json:"id"`
	Number        string       `json:"number"`
	State         State        `json:"state"`
	Summary       string       `json:"summary"`
	TotalAmount   float64      `json:"totalAmount"`
	ReimburseDate *time.Time   `json:"reimburseDate"`
	Remark        string       `json:"remark"`
	Details       []DetailDTO  `json:"details"`
	Attachments   []attach.DTO `json:"attachments"`
}

type DetailDTO struct {
	ID     uint    `json:"id"`
	Name   string  `json:"name"`
	Amount float64 `json:"amount"`
}

func (r *Reimburse) ToDTO(attaches []attach.Attach) *DTO {
	details := make([]DetailDTO, len(r.Details))
	for i, d := range r.Details {
		details[i] = *d.ToDTO()
	}
	attachDTOes := make([]attach.DTO, len(attaches))
	for i, d := range attaches {
		attachDTOes[i] = *d.ToDTO()
	}
	base := r.ToBaseDTO()
	base.Details = details
	base.Attachments = attachDTOes
	return base
}

func (r *Reimburse) ToBaseDTO() *DTO {
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

func ToBaseDTOs(entities []Reimburse) []DTO {
	details := make([]DTO, len(entities))
	for i, d := range entities {
		details[i] = *(d.ToBaseDTO())
	}
	return details
}

func (d *Detail) ToDTO() *DetailDTO {
	return &DetailDTO{
		ID:     d.ID,
		Name:   d.Name,
		Amount: d.Amount,
	}
}

func (r *DTO) ToEntity() *Reimburse {
	entity := Reimburse{
		ID:            r.ID,
		Number:        r.Number,
		State:         r.State,
		Summary:       r.Summary,
		TotalAmount:   r.TotalAmount,
		ReimburseDate: r.ReimburseDate,
		Remark:        r.Remark,
	}
	details := make([]Detail, len(r.Details))
	for i, d := range r.Details {
		details[i] = Detail{
			ID:     d.ID,
			Name:   d.Name,
			Amount: d.Amount,
		}
	}
	entity.Details = details
	return &entity
}
