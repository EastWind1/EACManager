package database

import (
	"fmt"

	"gorm.io/gorm"
)

type BaseEntity struct {
	ID uint `gorm:"primaryKey"`
}

func (e *BaseEntity) BeforeCreate(tx *gorm.DB) (err error) {
	if e.ID == 0 {
		var nextId uint
		err = tx.Raw(fmt.Sprintf("select nextval('%s_seq')", tx.Statement.Table)).Scan(&nextId).Error
		if err != nil {
			return
		}
		e.ID = nextId
	}
	return
}
