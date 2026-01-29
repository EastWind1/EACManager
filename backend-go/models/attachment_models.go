package models

import (
	"time"
)

// 定义枚举类型

// AttachmentType 附件类型
const (
	ATTACHMENT_TYPE_IMAGE = "IMAGE"
	ATTACHMENT_TYPE_PDF   = "PDF"
	ATTACHMENT_TYPE_WORD  = "WORD"
	ATTACHMENT_TYPE_EXCEL = "EXCEL"
	ATTACHMENT_TYPE_OTHER = "OTHER"
)

type AttachmentType string

// FileOperationType 文件操作类型
type FileOperationType string

const (
	FILE_OPERATION_TYPE_MOVE   = "MOVE"
	FILE_OPERATION_TYPE_COPY   = "COPY"
	FILE_OPERATION_TYPE_DELETE = "DELETE"
	FILE_OPERATION_TYPE_RENAME = "RENAME"
)

// FileOperation 文件操作
type FileOperation struct {
	Type    FileOperationType `json:"type"`
	Source  string            `json:"source,omitempty"`
	Target  string            `json:"target,omitempty"`
	OldName string            `json:"oldName,omitempty"`
	NewName string            `json:"newName,omitempty"`
}

// FileTxResult 文件事务结果
type FileTxResult struct {
	Success bool            `json:"success"`
	Message string          `json:"message,omitempty"`
	Files   []FileOperation `json:"files,omitempty"`
}

// AttachmentDTO 附件DTO
type AttachmentDTO struct {
	ID            int       `json:"id"`
	Name          string    `json:"name"`
	Type          string    `json:"type"`
	RelativePath  string    `json:"relativePath"`
	Size          int64     `json:"size"`
	MimeType      string    `json:"mimeType"`
	Description   string    `json:"description,omitempty"`
	IsTemp        bool      `json:"isTemp"`
	DownloadCount int       `json:"downloadCount"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// AttachmentCreateDTO 创建附件DTO
type AttachmentCreateDTO struct {
	Name         string `json:"name" binding:"required"`
	Type         string `json:"type"`
	RelativePath string `json:"relativePath"`
	Size         int64  `json:"size"`
	MimeType     string `json:"mimeType"`
	Description  string `json:"description"`
	IsTemp       bool   `json:"isTemp"`
}

// AttachmentUpdateDTO 更新附件DTO
type AttachmentUpdateDTO struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

// UploadResult 上传结果
type UploadResult struct {
	Name         string `json:"name"`
	Type         string `json:"type"`
	RelativePath string `json:"relativePath"`
	Size         int64  `json:"size"`
	MimeType     string `json:"mimeType"`
	IsTemp       bool   `json:"isTemp"`
}

// BillAttachRelation 单据附件关系
type BillAttachRelation struct {
	gorm.Model
	BillID       int    `gorm:"index;not null" json:"billId"`
	AttachmentID int    `gorm:"index;not null" json:"attachmentId"`
	BillType     string `json:"billType"` // enum: SERVICE_BILL, REIMBURSEMENT, ATTACHMENT
	Order        int    `gorm:"default:0" json:"order"`
}

func (BillAttachRelation) TableName() string {
	return "bill_attach_relation"
}
