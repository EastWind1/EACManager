package user

import (
	"backend-go/internal/security"
)

// UserProfile 用户资料表，与安全模块账户 sys_user 通过 user_id 对应。
type UserProfile struct {
	UserID uint `gorm:"primaryKey;column:user_id"`
	Name   string
	Phone  string
	Email  string
	security.Audit
}

func (*UserProfile) TableName() string { return "user_profile" }

type InitStatus struct {
	Required bool `json:"required"`
}

// DTO 用户接口响应：嵌入统一鉴权账户，补充资料字段。
type DTO struct {
	security.AuthorityUser
	Name  string `json:"name" validate:"required,notblank,max=64" label:"姓名"`
	Phone string `json:"phone" validate:"omitempty,phone" label:"手机号"`
	Email string `json:"email" validate:"omitempty,email,max=128" label:"邮箱"`
}

type LoginResult struct {
	Token string `json:"token"`
	User  DTO    `json:"user"`
}
