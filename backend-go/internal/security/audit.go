package security

import (
	"backend-go/pkg/auth"
	"time"

	"gorm.io/gorm"
)

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

// BeforeUpdate 记录修改人，并把审计列补进受限的更新列清单
//
// Select 限定列时，未列出的列不会进 SET，钩子里刚写好的审计字段会被静默丢掉；这里在钩子内补上，
// 调用方就不必在每处 Select 都记着写审计列。Selects 为空表示不限制列，此时不能追加（追加反而会
// 变成只更新这两列）。
func (a *Audit) BeforeUpdate(db *gorm.DB) (err error) {
	if len(db.Statement.Selects) > 0 {
		db.Statement.Selects = append(db.Statement.Selects, "last_modified_by_id", "last_modified_date")
	}
	user, err := auth.GetCurrentUser(db.Statement.Context)
	if err != nil {
		return
	}
	a.LastModifiedByID = user.GetID()
	return
}
