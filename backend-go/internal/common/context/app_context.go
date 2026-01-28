package context

import (
	"backend-go/config"
	"backend-go/internal/common/cache"

	"github.com/gofiber/fiber/v2"
	"gorm.io/gorm"
)

// AppContext 应用上下文
type AppContext struct {
	// cfg 配置
	Cfg *config.Config
	// cache 缓存
	Cache *cache.Cache
	// db 数据库链接
	Db *gorm.DB
	// server 服务
	Server *fiber.App
}
