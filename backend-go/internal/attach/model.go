package attach

import (
	"backend-go/pkg/audit"
	"backend-go/pkg/errs"
	"encoding/json/v2"
)

// Type 附件类型
type Type uint

const (
	// Image 图片
	Image Type = iota
	// PDF PDF
	PDF
	// Word Word
	Word
	// Excel Excel
	Excel
	// Other 其他
	Other
)

func (s *Type) MarshalJSON() ([]byte, error) {
	str := ""
	switch *s {
	case Image:
		str = "IMAGE"
	case PDF:
		str = "PDF"
	case Word:
		str = "WORD"
	case Excel:
		str = "EXCEL"
	case Other:
		str = "OTHER"
	}
	return json.Marshal(str)
}

func (s *Type) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "IMAGE":
			*s = Image
		case "PDF":
			*s = PDF
		case "WORD":
			*s = Word
		case "EXCEL":
			*s = Excel
		case "OTHER":
			*s = Other
		default:
			return errs.NewBizError("不支持的附件类型")
		}
	}
	return nil
}

// UnmarshalText 用于查询参数反序列化
func (s *Type) UnmarshalText(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		switch str {
		case "IMAGE":
			*s = Image
		case "PDF":
			*s = PDF
		case "WORD":
			*s = Word
		case "EXCEL":
			*s = Excel
		case "OTHER":
			*s = Other
		default:
			return errs.NewBizError("不支持的附件类型")
		}
	}
	return nil
}

// Attachment 附件
type Attachment struct {
	ID           uint `gorm:"primaryKey"`
	Name         string
	Type         Type
	RelativePath string
	audit.Audit
}

// AttachmentDTO 附件DTO
type AttachmentDTO struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
	Type Type   `json:"type"`
}

// ToDTO 转换为DTO
func (a *Attachment) ToDTO() *AttachmentDTO {
	return &AttachmentDTO{
		ID:   int(a.ID),
		Name: a.Name,
		Type: a.Type,
	}
}

// TOEntity 转换为DTO
func (a *AttachmentDTO) TOEntity() *Attachment {
	return &Attachment{
		ID:   uint(a.ID),
		Name: a.Name,
		Type: a.Type,
	}
}

// ToDTOs 转换为 DTOs
func ToDTOs(a []Attachment) []AttachmentDTO {
	if a == nil {
		return nil
	}
	attaches := make([]AttachmentDTO, len(a))
	for i, attachment := range a {
		attaches[i] = *attachment.ToDTO()
	}
	return attaches
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
	ID       uint `gorm:"primaryKey"`
	BillId   uint
	BillType BillType `gorm:"index"`
	AttachId uint
	Attach   Attachment `gorm:"foreignKey:AttachId"`
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
