package security

const AdminRoleCode = "ADMIN"

type RoleDTO struct {
	ID        uint   `json:"id,omitempty"`
	Code      string `json:"code"`
	Name      string `json:"name"`
	SortOrder int    `json:"sortOrder"`
	Disabled  bool   `json:"disabled"`
}

func (r *RoleDTO) ToEntity() *Role {
	return &Role{
		ID:        r.ID,
		Code:      r.Code,
		Name:      r.Name,
		SortOrder: r.SortOrder,
		Disabled:  r.Disabled,
	}
}

type Role struct {
	ID        uint   `gorm:"primaryKey"`
	Code      string `gorm:"not null;size:64;uniqueIndex"`
	Name      string `gorm:"not null;size:64"`
	SortOrder int    `gorm:"not null;default:0"`
	Disabled  bool   `gorm:"not null;default:false"`
	Audit
}

func (*Role) TableName() string { return "sys_role" }

func (r *Role) ToDTO() *RoleDTO {
	return &RoleDTO{
		ID:        r.ID,
		Code:      r.Code,
		Name:      r.Name,
		SortOrder: r.SortOrder,
		Disabled:  r.Disabled,
	}
}

type UserRole struct {
	ID     uint `gorm:"primaryKey"`
	UserID uint `gorm:"not null;index"`
	RoleID uint `gorm:"not null;index"`
	Audit
}

func (*UserRole) TableName() string { return "sys_user_role" }

type Permission struct {
	ID         uint   `gorm:"primaryKey"`
	Code       string `gorm:"not null;size:64;uniqueIndex"`
	Module     string `gorm:"not null;size:32;index"`
	ModuleName string `gorm:"not null;size:64"`
	Name       string `gorm:"not null;size:64"`
	SortOrder  int    `gorm:"not null;default:0"`
	Audit
}

type PermissionDTO struct {
	ID         uint   `json:"id"`
	Code       string `json:"code"`
	Module     string `json:"module"`
	ModuleName string `json:"moduleName"`
	Name       string `json:"name"`
	SortOrder  int    `json:"sortOrder"`
}

func (*Permission) TableName() string { return "sys_permission" }

func (p *Permission) ToDTO() *PermissionDTO {
	return &PermissionDTO{
		ID:         p.ID,
		Code:       p.Code,
		Module:     p.Module,
		ModuleName: p.ModuleName,
		Name:       p.Name,
		SortOrder:  p.SortOrder,
	}
}

// NewPermissionDTO 创建用于路由校验的权限值
func NewPermissionDTO(id uint, code, module, moduleName, name string, sortOrder int) PermissionDTO {
	return PermissionDTO{
		ID:         id,
		Code:       code,
		Module:     module,
		ModuleName: moduleName,
		Name:       name,
		SortOrder:  sortOrder,
	}
}

func (p *PermissionDTO) ToEntity() *Permission {
	return &Permission{
		ID:         p.ID,
		Code:       p.Code,
		Module:     p.Module,
		ModuleName: p.ModuleName,
		Name:       p.Name,
		SortOrder:  p.SortOrder,
	}
}

type RolePermission struct {
	ID           uint `gorm:"primaryKey"`
	RoleID       uint `gorm:"not null;index"`
	PermissionID uint `gorm:"not null;index"`
	Audit
}

func (*RolePermission) TableName() string { return "sys_role_permission" }
