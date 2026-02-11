package logger

import (
	"backend-go/config"

	"github.com/gofiber/fiber/v2/log"
)

var levelStr = []string{
	"trace",
	"debug",
	"info",
	"warn",
	"error",
	"fatal",
	"panic",
}

func getLevel(str string) log.Level {
	for i, v := range levelStr {
		if v == str {
			return log.Level(i)
		}
	}
	return log.Level(0)
}

// InitLogger 配置日志
func InitLogger(cfg *config.LogConfig) {
	level := getLevel(cfg.Level)
	log.SetLevel(level)
}
