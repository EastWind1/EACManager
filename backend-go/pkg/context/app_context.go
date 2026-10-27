package context

import (
	"backend-go/config"
	"backend-go/pkg/cache"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
)

type AppContext struct {
	Cfg *config.Config

	Cache cache.Cache

	Db *gorm.DB

	Server *fiber.App
}
