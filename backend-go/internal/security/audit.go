package security

import (
	"backend-go/pkg/auth"
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

func (a *Audit) BeforeCreate(db *gorm.DB) (err error) {
	user, err := auth.GetCurrentUser(db.Statement.Context)
	if err != nil {
		return
	}
	a.CreatedByID = user.GetID()
	return
}

func (a *Audit) BeforeUpdate(db *gorm.DB) (err error) {
	user, err := auth.GetCurrentUser(db.Statement.Context)
	if err != nil {
		return
	}
	a.LastModifiedByID = user.GetID()
	return
}
