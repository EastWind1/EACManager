package cache

import (
	"backend-go/config"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/gofiber/fiber/v3/log"
	"github.com/redis/go-redis/v9"
)

// Cache 是应用共享缓存的最小接口。
//
// 值统一按 JSON 存取；flag 用于登出黑名单这类只需要判断「是否存在」的场景。
type Cache interface {
	GetJSON(name string, key string, dest any) bool
	SetJSON(name string, key string, value any, expire time.Duration)
	SetFlag(name string, key string, expire time.Duration)
	HasFlag(name string, key string) bool
	Delete(name string, key string)
	DeleteName(name string)
	Clear()
}

const redisPrefix = "eac"

type RedisCache struct {
	client *redis.Client
}

func NewCache(cfg *config.Config) (Cache, error) {
	timeout := time.Duration(cfg.Redis.Timeout) * time.Second
	if timeout <= 0 {
		timeout = 2 * time.Second
	}
	client := redis.NewClient(&redis.Options{
		Addr:         cfg.Redis.Addr(),
		Password:     cfg.Redis.Password,
		DB:           cfg.Redis.DB,
		DialTimeout:  timeout,
		ReadTimeout:  timeout,
		WriteTimeout: timeout,
	})
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("连接 Redis 缓存失败: %w", err)
	}
	log.Infof("Redis 缓存已连接: %s db=%d", cfg.Redis.Addr(), cfg.Redis.DB)
	return &RedisCache{client: client}, nil
}

func (c *RedisCache) GetJSON(name string, key string, dest any) bool {
	raw, err := c.client.Get(context.Background(), redisKey(name, key)).Bytes()
	if err == nil {
		return json.Unmarshal(raw, dest) == nil
	}
	if errors.Is(err, redis.Nil) {
		return false
	}
	log.Warnf("读取 Redis 缓存失败: %v", err)
	return false
}

func (c *RedisCache) SetJSON(name string, key string, value any, expire time.Duration) {
	raw, err := json.Marshal(value)
	if err != nil {
		log.Warnf("序列化 Redis 缓存值失败: %v", err)
		return
	}
	if err := c.client.Set(context.Background(), redisKey(name, key), raw, expire).Err(); err != nil {
		log.Warnf("写入 Redis 缓存失败: %v", err)
	}
}

func (c *RedisCache) SetFlag(name string, key string, expire time.Duration) {
	if err := c.client.Set(context.Background(), redisKey(name, key), "1", expire).Err(); err != nil {
		log.Warnf("写入 Redis 缓存失败: %v", err)
	}
}

func (c *RedisCache) HasFlag(name string, key string) bool {
	n, err := c.client.Exists(context.Background(), redisKey(name, key)).Result()
	if err != nil {
		log.Warnf("读取 Redis 缓存失败: %v", err)
		return false
	}
	return n > 0
}

func (c *RedisCache) Delete(name string, key string) {
	if err := c.client.Del(context.Background(), redisKey(name, key)).Err(); err != nil {
		log.Warnf("删除 Redis 缓存失败: %v", err)
	}
}

func (c *RedisCache) DeleteName(name string) {
	c.deletePattern(fmt.Sprintf("%s:%s:*", redisPrefix, name))
}

func (c *RedisCache) Clear() {
	c.deletePattern(fmt.Sprintf("%s:*", redisPrefix))
}

func (c *RedisCache) deletePattern(pattern string) {
	var cursor uint64
	for {
		keys, next, err := c.client.Scan(context.Background(), cursor, pattern, 500).Result()
		if err != nil {
			log.Warnf("清理 Redis 缓存失败: %v", err)
			return
		}
		if len(keys) > 0 {
			if err := c.client.Del(context.Background(), keys...).Err(); err != nil {
				log.Warnf("删除 Redis 缓存失败: %v", err)
				return
			}
		}
		cursor = next
		if cursor == 0 {
			return
		}
	}
}

func redisKey(name string, key string) string {
	return fmt.Sprintf("%s:%s:%s", redisPrefix, name, key)
}
