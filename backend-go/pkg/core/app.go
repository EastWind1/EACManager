package core

import (
	"backend-go/config"
	"backend-go/pkg/cache"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
)

// App 聚合应用级基础设施
type App struct {
	Config *config.Config
	Cache  cache.Cache
	DB     *gorm.DB
	Server *fiber.App
	Router fiber.Router
}
