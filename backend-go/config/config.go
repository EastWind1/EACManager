package config

import (
	"fmt"

	"github.com/gofiber/fiber/v2/log"
	"github.com/spf13/viper"
)

// Config 配置
type Config struct {
	Server     *ServerConfig
	Log        *LogConfig
	Db         *DatabaseConfig
	JWT        *JWTConfig
	Cache      *CacheConfig
	Attachment *AttachmentConfig
}

// LogConfig 日志配置
type LogConfig struct {
	Level string
}

// ServerConfig 服务器配置
type ServerConfig struct {
	Port int
	Mode string
}

// DatabaseConfig 数据库配置
type DatabaseConfig struct {
	Host     string
	Port     int
	Name     string
	Username string
	Password string
	SSLMode  string
}

// CacheConfig 缓存配置
type CacheConfig struct {
	// Expire 过期时间，单位秒
	Expire int
}

// GetDBStr 获取数据库连接字符串
func (d DatabaseConfig) GetDBStr() string {
	return fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		d.Host, d.Port, d.Username, d.Password, d.Name, d.SSLMode)
}

// JWTConfig JWT配置
type JWTConfig struct {
	Secret string
	Expire int
}

// AttachmentConfig 附件配置
type AttachmentConfig struct {
	Path        string
	Temp        string
	MaxFileSize int64
}

// NewConfig 加载配置
func NewConfig() *Config {
	viper.AddConfigPath("./config")
	viper.AddConfigPath(".")

	// 读取 yaml
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")

	if err := viper.ReadInConfig(); err != nil {
		log.Fatalf("Failed to read config file: %v", err)
	}
	// 读取环境变量
	viper.AutomaticEnv()

	log.Infof("load config file: %s", viper.ConfigFileUsed())
	log.Infof("use config: %v", viper.AllSettings())

	config := &Config{}
	if err := viper.Unmarshal(config); err != nil {
		log.Fatalf("Failed to unmarshal config: %v", err)
	}

	return config
}
