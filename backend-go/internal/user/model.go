package user

import "backend-go/internal/security"

type User struct {
	ID       uint   `gorm:"primaryKey"`
	Username string `gorm:"uniqueIndex"`
	Password string
	Name     string
	Phone    string
	Email    string
	Disabled bool
	security.Audit
}

func (u *User) TableName() string {
	return "sys_user"
}

func (u *User) GetID() uint {
	return u.ID
}

type InitDTO struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Name     string `json:"name"`
	Phone    string `json:"phone"`
	Email    string `json:"email"`
}

type InitStatus struct {
	Required bool `json:"required"`
}

type DTO struct {
	ID          uint                     `json:"id"`
	Username    string                   `json:"username"`
	Password    *string                  `json:"password"`
	Name        string                   `json:"name"`
	Phone       string                   `json:"phone"`
	Email       string                   `json:"email"`
	Disabled    bool                     `json:"disabled"`
	Roles       []security.RoleDTO       `json:"roles"`
	Permissions []security.PermissionDTO `json:"permissions,omitempty"`
}

func (u *User) ToDTO() *DTO {
	return &DTO{
		ID:       u.ID,
		Username: u.Username,
		// 密码不进行映射
		Name:     u.Name,
		Phone:    u.Phone,
		Email:    u.Email,
		Disabled: u.Disabled,
	}
}

func (u *DTO) ToEntity() *User {
	return &User{
		ID:       u.ID,
		Username: u.Username,
		Password: *u.Password,
		Name:     u.Name,
		Phone:    u.Phone,
		Email:    u.Email,
		Disabled: u.Disabled,
	}
}

func ToDTOs(users []User) []DTO {
	if users == nil {
		return nil
	}
	userDTOs := make([]DTO, len(users))
	for i, user := range users {
		userDTOs[i] = *user.ToDTO()
	}
	return userDTOs
}
