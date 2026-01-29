package model

import "backend-go/internal/common/audit"

// AttachType 附件类型
type AttachType uint

const (
	// AttachTypeImage 图片
	AttachTypeImage AttachType = iota
	// AttachTypePDF PDF
	AttachTypePDF
	// AttachTypeWord Word
	AttachTypeWord
	// AttachTypeExcel Excel
	AttachTypeExcel
	// AttachTypeOther 其他
	AttachTypeOther
)

// Attachment 附件
type Attachment struct {
	ID           int `gorm:"primaryKey;defalut:nextval('attachment_seq')"`
	Name         string
	Type         AttachType `gorm:"default:4"`
	RelativePath string
	audit.Entity
}

// AttachmentDTO 附件DTO
type AttachmentDTO struct {
	ID           int        `json:"id"`
	Name         string     `json:"name"`
	Type         AttachType `json:"type"`
	RelativePath string     `json:"relativePath"`
	Temp         bool       `json:"temp"`
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
func ToDTOs(a []*Attachment) *[]AttachmentDTO {
	if a == nil {
		return nil
	}
	dtos := make([]AttachmentDTO, len(a))
	for i, attachment := range a {
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
	ID       int      `gorm:"primaryKey"`
	BillId   int      `gorm`
	BillType BillType `gorm:"index"`
	AttachId int
	Attach   Attachment
}

// FileOpType 文件操作类型
type FileOpType int

const (
	FileOpTypeCreate FileOpType = iota
	FileOpTypeMove
	FileOpTypeCopy
	FileOpTypeDelete
)

// FileOp 文件操作
type FileOp struct {
	Type   FileOpType
	Origin string
	Target string
}
