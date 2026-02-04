package database

import (
	"backend-go/config"

	"github.com/gofiber/fiber/v2/log"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/schema"
)

// NewDB 初始化 Postgres 数据库
func NewDB(cfg *config.DatabaseConfig, logCfg *config.LogConfig) *gorm.DB {
	// 连接数据库
	db, err := gorm.Open(postgres.Open(cfg.GetDBStr()), &gorm.Config{
		NamingStrategy: schema.NamingStrategy{
			// 单数表名
			SingularTable: true,
		},
	})
	if err != nil {
		log.Fatalf("初始化数据库链接失败：%v", err)
	}
	if logCfg.Level == "debug" || logCfg.Level == "trace" {
		return db.Debug()
	}
	return db
}
