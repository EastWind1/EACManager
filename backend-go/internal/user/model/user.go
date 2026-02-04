package model

import (
	"backend-go/internal/common/audit"
	"backend-go/internal/common/auth"
	"backend-go/internal/common/database"
)

// User 用户实体
type User struct {
	database.BaseEntity
	Username  string `gorm:"uniqueIndex"`
	Password  string
	Name      string
	Phone     string
	Email     string
	Authority auth.AuthorityRole `gorm:"default:'ROLE_USER'"`
	IsEnabled bool               `gorm:"default:true"`
	audit.Entity
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
	IsEnabled bool               `json:"isEnabled"`
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
		IsEnabled: u.IsEnabled,
	}
}

// ToEntity 创建用户实体
func (u *UserDTO) ToEntity() *User {
	return &User{
		BaseEntity: database.BaseEntity{
			ID: u.ID,
		},
		Username:  u.Username,
		Password:  *u.Password,
		Name:      u.Name,
		Phone:     u.Phone,
		Email:     u.Email,
		Authority: u.Authority,
		IsEnabled: u.IsEnabled,
	}
}

// ToDTOs 创建用户 DTO 列表
func ToDTOs(users *[]User) *[]UserDTO {
	if users == nil {
		return nil
	}
	userDTOs := make([]UserDTO, len(*users))
	for i, user := range *users {
		userDTOs[i] = *user.ToDTO()
	}
	return &userDTOs
}
