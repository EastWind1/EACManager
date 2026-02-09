package audit

import (
	"backend-go/internal/common/auth"
	"time"

	"gorm.io/gorm"
)

// Audit 审计实体
type Audit struct {
	CreatedDate      time.Time `gorm:"autoCreateTime;type:timestamptz"`
	CreatedByID      uint
	LastModifiedDate time.Time `gorm:"autoUpdateTime;type:timestamptz"`
	LastModifiedByID uint
}

// 由于 Gorm 嵌套同名钩子会相互抵消，由使用方显式调用

func (a *Audit) SetCreator(db *gorm.DB) (err error) {
	user, err := auth.GetCurrentUser(db.Statement.Context)
	if err != nil {
		return
	}
	id := user.GetID()
	a.CreatedByID = id
	return
}

func (a *Audit) SetModifier(db *gorm.DB) (err error) {
	user, err := auth.GetCurrentUser(db.Statement.Context)
	if err != nil {
		return
	}
	id := user.GetID()
	a.LastModifiedByID = id
	return
}
