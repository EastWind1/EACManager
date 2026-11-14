package security

// Account 账户表 sys_user：只保存登录名、密码和启用状态。
type Account struct {
	ID       uint   `gorm:"primaryKey"`
	Username string `gorm:"uniqueIndex"`
	Password string
	Disabled bool
	Audit
}

func (*Account) TableName() string { return "sys_user" }

// AuthorityUser 统一鉴权账户：认证主体与账户信息共用一份结构，不携带审计字段。
type AuthorityUser struct {
	ID          uint            `json:"id"`
	Username    string          `json:"username"`
	Password    string          `json:"password,omitempty"`
	Disabled    bool            `json:"disabled"`
	Roles       []RoleDTO       `json:"roles"`
	Permissions []PermissionDTO `json:"permissions,omitempty"`
}

func (a *Account) ToDTO() *AuthorityUser {
	return &AuthorityUser{
		ID:       a.ID,
		Username: a.Username,
		Password: a.Password,
		Disabled: a.Disabled,
	}
}

func (u *AuthorityUser) GetID() uint { return u.ID }

type LoginResult struct {
	Token   string        `json:"token"`
	Account AuthorityUser `json:"account"`
}
