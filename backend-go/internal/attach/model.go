package attach

import (
	"backend-go/internal/security"
	"backend-go/pkg/errs"
	"encoding/json/v2"
)

type Type uint

const (
	Image Type = iota
	PDF
	Word
	Excel
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

type Attach struct {
	ID           uint `gorm:"primaryKey"`
	Name         string
	Type         Type
	RelativePath string
	Status       Status `gorm:"index"`
	security.Audit
}

func (a *Attach) TableName() string {
	return "attachment"
}

type DTO struct {
	ID     uint   `json:"id"`
	Name   string `json:"name"`
	Type   Type   `json:"type"`
	Status Status `json:"status"`
}

func (a *Attach) ToDTO() *DTO {
	return &DTO{
		ID:     a.ID,
		Name:   a.Name,
		Type:   a.Type,
		Status: a.Status,
	}
}

func (a *DTO) TOEntity() *Attach {
	return &Attach{
		ID:     a.ID,
		Name:   a.Name,
		Type:   a.Type,
		Status: a.Status,
	}
}

func ToDTOs(a []Attach) []DTO {
	if a == nil {
		return nil
	}
	attaches := make([]DTO, len(a))
	for i, attachment := range a {
		attaches[i] = *attachment.ToDTO()
	}
	return attaches
}

type Status uint

const (
	// StatusTemp 临时态，由临时目录管理
	StatusTemp Status = iota
	// StatusTransient 瞬时态，由定时任务清理
	StatusTransient
	// StatusPersistent 持久态
	StatusPersistent
)

func (s Status) String() string {
	switch s {
	case StatusTemp:
		return "TEMP"
	case StatusTransient:
		return "TRANSIENT"
	case StatusPersistent:
		return "PERSISTENT"
	default:
		return ""
	}
}

func (s *Status) MarshalJSON() ([]byte, error) {
	return json.Marshal(s.String())
}

func (s *Status) UnmarshalJSON(data []byte) error {
	var str string
	if err := json.Unmarshal(data, &str); err != nil {
		return errs.NewBizError("不支持的附件状态")
	}
	return s.setFromString(str)
}

func (s *Status) UnmarshalText(data []byte) error {
	return s.setFromString(string(data))
}

func (s *Status) setFromString(str string) error {
	switch str {
	case "TEMP":
		*s = StatusTemp
	case "TRANSIENT":
		*s = StatusTransient
	case "PERSISTENT":
		*s = StatusPersistent
	default:
		return errs.NewBizError("不支持的附件状态")
	}
	return nil
}

type FileOpType uint

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

type FileOp struct {
	Type   FileOpType
	Origin string
	Target string
	// SoftDelete 表示该 Move 只是「先移走以便回滚」的删除；事务提交后需要真正删掉目标文件
	SoftDelete bool
}
