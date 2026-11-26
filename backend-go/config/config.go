package config

import (
	"embed"
	"fmt"
	"strings"

	"github.com/gofiber/fiber/v3/log"
	"github.com/spf13/viper"
)

//go:embed config.yaml
var embedFS embed.FS

type Config struct {
	Server *ServerConfig
	Log    *LogConfig
	Db     *DatabaseConfig
	JWT    *JWTConfig    `mapstructure:"jwt"`
	Cache  *CacheConfig  `mapstructure:"cache"`
	Redis  *RedisConfig  `mapstructure:"redis"`
	OCR    *OCRConfig    `mapstructure:"ocr"`
	Attach *AttachConfig `mapstructure:"attachment"`
}

type LogConfig struct {
	Level string
}

type ServerConfig struct {
	Port int
}

type DatabaseConfig struct {
	Host     string
	Port     int
	Name     string
	Username string
	Password string
	SSLMode  string `mapstructure:"sslmode"`
	// 连接池上限，缺省时由 database 包补默认值
	MaxOpenConns int
	MaxIdleConns int
	// 单位分钟
	ConnMaxLifetime int
}

type CacheConfig struct {
	// 缓存有效期（秒），与 Java spring.cache.redis.time-to-live 对齐
	Expire int `mapstructure:"expire"`
}

type RedisConfig struct {
	Enabled  bool
	Host     string
	Port     int
	Password string
	DB       int `mapstructure:"db"`
	// 连接/读写超时，单位秒
	Timeout int
}

func (r RedisConfig) Addr() string {
	return fmt.Sprintf("%s:%d", r.Host, r.Port)
}

type OCRConfig struct {
	// 完整端点（含路径），不是主机名
	URL string `mapstructure:"url"`
}

func (d DatabaseConfig) GetDBStr() string {
	return fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		d.Host, d.Port, d.Username, d.Password, d.Name, d.SSLMode)
}

type JWTConfig struct {
	Secret string
	// token 有效期，单位秒
	Expire int
}

type AttachConfig struct {
	Path        string
	MaxFileSize int64 `mapstructure:"maxFileSize"`
}

// NewConfig 读取 config.yaml 与环境变量；文件缺失或解析失败时直接终止进程
func NewConfig() *Config {
	f, _ := embedFS.Open("config.yaml")
	defer f.Close()
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	if err := viper.ReadConfig(f); err != nil {
		log.Fatalf("加载内嵌配置文件失败：%v", err)
	}
	viper.AddConfigPath(".")
	if err := viper.ReadInConfig(); err != nil {
		log.Infof("未找到本地配置文件: %v", err)
	}
	viper.AutomaticEnv()
	viper.SetEnvKeyReplacer(strings.NewReplacer("-", "_", ".", "_"))
	log.Infof("加载配置文件 %s", viper.ConfigFileUsed())

	config := &Config{}
	if err := viper.Unmarshal(config); err != nil {
		log.Fatalf("反序列化配置文件失败: %v", err)
	}
	if config.Server == nil || config.Log == nil || config.Db == nil || config.Attach == nil || config.Cache == nil {
		log.Fatalf("配置不完整")
	}
	if config.JWT == nil || len(config.JWT.Secret) < 32 {
		log.Fatalf("jwt.secret 未配置或不足 32 字节：请通过 JWT_SECRET 环境变量注入")
	}
	if config.JWT.Expire <= 0 {
		log.Fatalf("jwt.expire 必须大于 0")
	}
	if config.Cache.Expire <= 0 {
		config.Cache.Expire = 300
	}
	log.Infof("配置: server.port=%d log.level=%s db.host=%s db.name=%s jwt.expire=%ds attachment.path=%s",
		config.Server.Port, config.Log.Level,
		config.Db.Host, config.Db.Name, config.JWT.Expire, config.Attach.Path)

	return config
}
