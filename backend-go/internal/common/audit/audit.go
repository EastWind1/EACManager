package audit

import (
	"backend-go/internal/common/auth"
	"time"

	"gorm.io/gorm"
)

// Entity 审计实体
type Entity struct {
	CreatedDate      time.Time `gorm:"autoCreateTime;type:timestamptz"`
	CreatedByID      uint
	LastModifiedDate time.Time `gorm:"autoUpdateTime;type:timestamptz"`
	LastModifiedByID uint
}

// BeforeCreate 插入前设置 ID, 创建人
func (entity *Entity) BeforeCreate(db *gorm.DB) (err error) {
	user := auth.GetCurrentUser(db.Statement.Context)
	if user == nil {
		return
	}
	id := user.GetID()
	entity.CreatedByID = id
	return
}

// BeforeUpdate 更新前设置修改人
func (entity *Entity) BeforeUpdate(db *gorm.DB) (err error) {
	user := auth.GetCurrentUser(db.Statement.Context)
	if user == nil {
		return
	}
	id := user.GetID()
	entity.LastModifiedByID = id
	return
}
