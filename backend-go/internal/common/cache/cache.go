// Package cache 缓存
package cache

import (
	"backend-go/config"
	"sync"
	"time"
)

// Cache 缓存
type Cache struct {
	cacheMap sync.Map
	expire   time.Duration
}

// cacheValue 缓存项
type cacheValue struct {
	value      any
	expireTime time.Time
}

// NewCache 初始化缓存
func NewCache(cfg *config.CacheConfig) *Cache {
	cache := &Cache{
		cacheMap: sync.Map{},
		expire:   time.Duration(cfg.Expire) * time.Second,
	}

	go func() {
		ticker := time.NewTicker(time.Hour * 24)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				cache.clearExpired()
			default:
				time.Sleep(time.Minute)
			}
		}
	}()

	return cache
}

// Get 获取缓存项
//
// name 命名空间，key 键
func (c *Cache) Get(name string, key string) (any, bool) {
	innerMap, ok := c.cacheMap.Load(name)
	if !ok {
		return nil, false
	}
	inner, ok := innerMap.(*sync.Map)
	if !ok {
		return nil, false
	}

	value, ok := inner.Load(key)
	if !ok {
		return nil, false
	}
	cacheVal, ok := value.(*cacheValue)
	if !ok {
		return nil, false
	}

	// 懒删除
	if cacheVal.expireTime.Before(time.Now()) {
		inner.Delete(key)
		return nil, false
	}

	return cacheVal.value, true
}

// Put 设置缓存项
//
// name 命名空间，key 键
func (c *Cache) Put(name string, key string, value any) {
	c.PutWithExpire(name, key, value, c.expire)
}

// PutWithExpire 设置缓存项, 包含过期时间
func (c *Cache) PutWithExpire(name string, key string, value any, expire time.Duration) {
	inner, _ := c.cacheMap.LoadOrStore(name, &sync.Map{})
	innerMap, ok := inner.(*sync.Map)
	if !ok {
		return
	}

	innerMap.Store(key, &cacheValue{
		value:      value,
		expireTime: time.Now().Add(expire),
	})
}

// Delete 删除缓存项
func (c *Cache) Delete(name string, key string) {
	inner, ok := c.cacheMap.Load(name)
	if !ok {
		return
	}
	innerMap, ok := inner.(*sync.Map)
	if !ok {
		return
	}

	innerMap.Delete(key)
}

// DeleteName 删除命名空间
func (c *Cache) DeleteName(name string) {
	c.cacheMap.Delete(name)
}

// Clear 清空缓存
func (c *Cache) Clear() {
	c.cacheMap.Clear()
}

// clearExpired 清理过期缓存
func (c *Cache) clearExpired() {
	c.cacheMap.Range(func(nsKey, nsValue any) bool {
		innerMap, ok := nsValue.(*sync.Map)
		if !ok {
			return true
		}

		innerMap.Range(func(key, value any) bool {
			cacheVal, ok2 := value.(*cacheValue)
			if !ok2 {
				return true
			}

			if cacheVal.expireTime.Before(time.Now()) {
				innerMap.Delete(key)
			}
			return true
		})
		return true
	})
}
