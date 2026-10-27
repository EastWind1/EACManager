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
	JWT    *JWTConfig `mapstructure:"jwt"`
	Cache  *CacheConfig
	OCR    *OCRConfig    `mapstructure:"ocr"`
	Attach *AttachConfig `mapstructure:"attachment"`
}

type LogConfig struct {
	Level string
}

type ServerConfig struct {
	Port int
	Mode string
}

type DatabaseConfig struct {
	Host     string
	Port     int
	Name     string
	Username string
	Password string
	SSLMode  string `mapstructure:"sslmode"`
	// 连接池上限，缺省时由 database 包补默认值
	MaxOpenConns int `mapstructure:"maxOpenConns"`
	MaxIdleConns int `mapstructure:"maxIdleConns"`
	// 单位分钟
	ConnMaxLifetime int `mapstructure:"connMaxLifetime"`
}

type CacheConfig struct {
	// 单位秒
	Expire int
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
	log.Infof("使用配置: %v", viper.AllSettings())

	config := &Config{}
	if err := viper.Unmarshal(config); err != nil {
		log.Fatalf("反序列化配置文件失败: %v", err)
	}

	return config
}
