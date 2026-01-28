package model

import "backend-go/internal/common/audit"

// AttachmentType 附件类型
type AttachmentType uint

const (
	// AttachmentTypeImage 图片
	AttachmentTypeImage AttachmentType = iota
	// AttachmentTypePDF PDF
	AttachmentTypePDF
	// AttachmentTypeWord Word
	AttachmentTypeWord
	// AttachmentTypeExcel Excel
	AttachmentTypeExcel
	// AttachmentTypeOther 其他
	AttachmentTypeOther
)

// Attachment 附件
type Attachment struct {
	ID           int `gorm:"primaryKey;defalut:nextval('attachment_seq')"`
	Name         string
	Type         AttachmentType `gorm:"default:4"`
	RelativePath string
	audit.Entity
}

// AttachmentDTO 附件DTO
type AttachmentDTO struct {
	ID           int            `json:"id"`
	Name         string         `json:"name"`
	Type         AttachmentType `json:"type"`
	RelativePath string         `json:"relativePath"`
}

// ToDTO 转换为DTO
func (a *Attachment) ToDTO() *AttachmentDTO {
	return &AttachmentDTO{
		ID:           a.ID,
		Name:         a.Name,
		Type:         a.Type,
		RelativePath: a.RelativePath,
	}
}

// TOEntity 转换为DTO
func (a *AttachmentDTO) TOEntity() *Attachment {
	return &Attachment{
		ID:           a.ID,
		Name:         a.Name,
		Type:         a.Type,
		RelativePath: a.RelativePath,
	}
}

// ToDTOs 转换为 DTOs
func ToDTOs(a *[]Attachment) *[]AttachmentDTO {
	if a == nil {
		return nil
	}
	dtos := make([]AttachmentDTO, len(*a))
	for i, attachment := range *a {
		dtos[i] = *attachment.ToDTO()
	}
	return &dtos
}

type BillType uint

const (
	BillTypeServiceBill BillType = iota
	BillTypeReimbursement
)

type BillAttachRelation struct {
	ID       int `json:"id" gorm:"primaryKey"`
	BillID   int
	BillType BillType `gorm:"index"`
	AttachID int
}
