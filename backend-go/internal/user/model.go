package user

import "backend-go/internal/security"

// User 用户实体
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

// InitDTO 首次管理员初始化参数
type InitDTO struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Name     string `json:"name"`
	Phone    string `json:"phone"`
	Email    string `json:"email"`
}

// InitStatus 初始化状态
type InitStatus struct {
	Required bool `json:"required"`
}

// DTO 用户 DTO
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

// ToDTO 创建用户 DTO
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

// ToEntity 创建用户实体
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

// ToDTOs 创建用户 DTO 列表
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
