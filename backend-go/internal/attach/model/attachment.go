package model

import (
	"backend-go/internal/common/audit"
	"backend-go/internal/common/database"
	"backend-go/internal/common/errs"

	"github.com/bytedance/sonic"
)

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

func (s *AttachType) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case AttachTypeImage:
		str = "IMAGE"
	case AttachTypePDF:
		str = "PDF"
	case AttachTypeWord:
		str = "WORD"
	case AttachTypeExcel:
		str = "EXCEL"
	case AttachTypeOther:
		str = "OTHER"

	}
	return sonic.Marshal(str)
}

func (s *AttachType) UnmarshalJSON(data []byte) error {
	var str string
	if err := sonic.Unmarshal(data, &str); err == nil {
		switch str {
		case "IMAGE":
			*s = AttachTypeImage
		case "PDF":
			*s = AttachTypePDF
		case "WORD":
			*s = AttachTypeWord
		case "EXCEL":
			*s = AttachTypeExcel
		case "OTHER":
			*s = AttachTypeOther
		default:
			return errs.NewBizError("不支持的附件类型")
		}
	}
	return nil
}

func (s *AttachType) UnmarshalText(data []byte) error {
	var str string
	if err := sonic.Unmarshal(data, &str); err == nil {
		switch str {
		case "IMAGE":
			*s = AttachTypeImage
		case "PDF":
			*s = AttachTypePDF
		case "WORD":
			*s = AttachTypeWord
		case "EXCEL":
			*s = AttachTypeExcel
		case "OTHER":
			*s = AttachTypeOther
		default:
			return errs.NewBizError("不支持的附件类型")
		}
	}
	return nil
}

// Attachment 附件
type Attachment struct {
	database.BaseEntity
	Name         string
	Type         AttachType `gorm:"default:4"`
	RelativePath string
	audit.Entity
}

// AttachmentDTO 附件DTO
type AttachmentDTO struct {
	ID           uint       `json:"id"`
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
		BaseEntity: database.BaseEntity{
			ID: a.ID,
		},
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

func (b BillType) String() string {
	switch b {
	case BillTypeServiceBill:
		return "SERVICE_BILL"
	case BillTypeReimbursement:
		return "REIMBURSEMENT"
	default:
		return "Unknown"
	}
}

type BillAttachRelation struct {
	database.BaseEntity
	BillId   uint
	BillType BillType `gorm:"index"`
	AttachId uint
	Attach   Attachment `gorm:"foreignkey:AttachId"`
}

// FileOpType 文件操作类型
type FileOpType int

const (
	FileOpCreate FileOpType = iota
	FileOpMove
	FileOpCopy
	FileOpDelete
)

func (f FileOpType) String() string {
	switch f {
	case FileOpCreate:
		return "Create"
	case FileOpMove:
		return "Move"
	case FileOpCopy:
		return "Copy"
	case FileOpDelete:
		return "Delete"
	default:
		return "Unknown"
	}
}

// FileOp 文件操作
type FileOp struct {
	Type   FileOpType
	Origin string
	Target string
}
