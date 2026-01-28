package database

import (
	"backend-go/config"

	"github.com/sirupsen/logrus"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/schema"
)

// NewDB 初始化 Postgres 数据库
func NewDB(cfg *config.DatabaseConfig) *gorm.DB {
	// 连接数据库
	db, err := gorm.Open(postgres.Open(cfg.GetDBStr()), &gorm.Config{
		NamingStrategy: schema.NamingStrategy{
			// 单数表名
			SingularTable: true,
		},
	})
	if err != nil {
		logrus.Fatalf("Failed to connect to database: %v", err)
	}
	return db
}
