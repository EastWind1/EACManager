package model

import (
	"backend-go/internal/pkg/audit"
	"backend-go/internal/pkg/auth"

	"gorm.io/gorm"
)

// User 用户实体
type User struct {
	ID        uint   `gorm:"primaryKey"`
	Username  string `gorm:"uniqueIndex"`
	Password  string
	Name      string
	Phone     string
	Email     string
	Authority auth.AuthorityRole `gorm:"default:'ROLE_USER'"`
	Disabled  bool               `gorm:"default:false"`
	audit.Audit
}

func (u *User) TableName() string {
	return "sys_user"
}

func (u *User) BeforeCreate(db *gorm.DB) (err error) {
	return u.Audit.SetCreator(db)
}

func (u *User) BeforeUpdate(db *gorm.DB) (err error) {
	return u.Audit.SetModifier(db)
}

func (u *User) GetID() uint {
	return u.ID
}

func (u *User) GetRole() auth.AuthorityRole {
	return u.Authority
}

// UserDTO 用户 DTO
type UserDTO struct {
	ID        uint               `json:"id"`
	Username  string             `json:"username"`
	Password  *string            `json:"password"`
	Name      string             `json:"name"`
	Phone     string             `json:"phone"`
	Email     string             `json:"email"`
	Authority auth.AuthorityRole `json:"authority"`
	Disabled  bool               `json:"disabled"`
}

// ToDTO 创建用户 DTO
func (u *User) ToDTO() *UserDTO {
	return &UserDTO{
		ID:       u.ID,
		Username: u.Username,
		// 密码不进行映射
		Name:      u.Name,
		Phone:     u.Phone,
		Email:     u.Email,
		Authority: u.Authority,
		Disabled:  u.Disabled,
	}
}

// ToEntity 创建用户实体
func (u *UserDTO) ToEntity() *User {
	return &User{
		ID:        u.ID,
		Username:  u.Username,
		Password:  *u.Password,
		Name:      u.Name,
		Phone:     u.Phone,
		Email:     u.Email,
		Authority: u.Authority,
		Disabled:  u.Disabled,
	}
}

// ToDTOs 创建用户 DTO 列表
func ToDTOs(users []User) []UserDTO {
	if users == nil {
		return nil
	}
	userDTOs := make([]UserDTO, len(users))
	for i, user := range users {
		userDTOs[i] = *user.ToDTO()
	}
	return userDTOs
}
