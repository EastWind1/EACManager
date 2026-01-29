package models

// ServiceBillBillAttachRelation 服务单据附件关系
type ServiceBillBillAttachRelation struct {
	gorm.Model
	ServiceBillID int `gorm:"index;not null" json:"serviceBillId"`
	AttachmentID  int `gorm:"index;not null" json:"attachmentId"`
	Order         int `gorm:"default:0" json:"order"`
}

func (ServiceBillBillAttachRelation) TableName() string {
	return "service_bill_attach_relation"
}

// ReimbursementBillAttachRelation 报销单附件关系
type ReimbursementBillAttachRelation struct {
	gorm.Model
	ReimbursementID int `gorm:"index;not null" json:"reimbursementId"`
	AttachmentID    int `gorm:"index;not null" json:"attachmentId"`
	Order           int `gorm:"default:0" json:"order"`
}

func (ReimbursementBillAttachRelation) TableName() string {
	return "reimbursement_attach_relation"
}

// AttachmentBillAttachRelation 附件附件关系
type AttachmentBillAttachRelation struct {
	gorm.Model
	AttachmentID int    `gorm:"index;not null" json:"attachmentId"`
	BillID       int    `gorm:"index;not null" json:"billId"`
	BillType     string `json:"billType"` // enum: SERVICE_BILL, REIMBURSEMENT
	Order        int    `gorm:"default:0" json:"order"`
}

func (AttachmentBillAttachRelation) TableName() string {
	return "attachment_bill_relation"
}
