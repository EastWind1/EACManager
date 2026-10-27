package database

import (
	"backend-go/config"
	"errors"
	"time"

	"github.com/gofiber/fiber/v3/log"
	"github.com/jackc/pgx/v5/pgconn"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
	"gorm.io/gorm/schema"
)

const (
	defaultMaxOpenConns    = 20
	defaultMaxIdleConns    = 20
	defaultConnMaxLifetime = time.Hour
	defaultConnMaxIdleTime = 10 * time.Minute
)

func NewDB(cfg *config.DatabaseConfig, logCfg *config.LogConfig) *gorm.DB {
	db, err := gorm.Open(postgres.New(postgres.Config{
		DSN: cfg.GetDBStr(),
	}), &gorm.Config{
		NamingStrategy: schema.NamingStrategy{
			SingularTable: true,
		},
		DisableForeignKeyConstraintWhenMigrating: true,
		// 没有事务时不要为每次写入额外开一个隐式事务
		SkipDefaultTransaction: true,
		// 语句缓存：与 pgx 侧的 PrepareStmt 不同，这里缓存的是 GORM 构建出的语句结构，
		// 省掉每次请求重复解析模型、拼装 SQL 的开销，也不依赖连接生命周期
		PrepareStmt: true,
		Logger:      newLogger(logCfg),
	})
	if err != nil {
		log.Fatalf("初始化数据库链接失败：%v", err)
	}
	configurePool(db, cfg)
	return db
}

// configurePool 设置连接池上限与回收策略
//
// database/sql 默认连接池没有上限（按并发无限开连接），一旦请求堆积就会打满数据库的
// max_connections；这里给出显式上限，并让空闲连接及时回收。
func configurePool(db *gorm.DB, cfg *config.DatabaseConfig) {
	sqlDB, err := db.DB()
	if err != nil {
		log.Fatalf("获取数据库连接池失败：%v", err)
	}
	sqlDB.SetMaxOpenConns(orDefault(cfg.MaxOpenConns, defaultMaxOpenConns))
	sqlDB.SetMaxIdleConns(orDefault(cfg.MaxIdleConns, defaultMaxIdleConns))
	lifetime := defaultConnMaxLifetime
	if cfg.ConnMaxLifetime > 0 {
		lifetime = time.Duration(cfg.ConnMaxLifetime) * time.Minute
	}
	sqlDB.SetConnMaxLifetime(lifetime)
	sqlDB.SetConnMaxIdleTime(defaultConnMaxIdleTime)
}

func orDefault(value, fallback int) int {
	if value <= 0 {
		return fallback
	}
	return value
}

// newLogger 日志级别跟随配置：debug/trace 打印每条 SQL，其余只输出慢查询与错误，
// 且一律输出参数化后的 SQL，避免把用户数据写进日志
func newLogger(logCfg *config.LogConfig) logger.Interface {
	level := logger.Warn
	if logCfg.Level == "debug" || logCfg.Level == "trace" || logCfg.Level == "info" {
		level = logger.Info
	}
	return logger.New(gormLogWriter{}, logger.Config{
		SlowThreshold:             200 * time.Millisecond,
		LogLevel:                  level,
		IgnoreRecordNotFoundError: true,
		ParameterizedQueries:      true,
		Colorful:                  false,
	})
}

// gormLogWriter 把 GORM 的日志接到 fiber 的日志上，保证输出格式一致
type gormLogWriter struct{}

func (gormLogWriter) Printf(format string, args ...any) {
	log.Debugf(format, args...)
}

// IsDuplicateKey 判断是否唯一键冲突（PostgreSQL 23505）
func IsDuplicateKey(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}
