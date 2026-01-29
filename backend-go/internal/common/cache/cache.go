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
	expireTime *time.Time
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

// GetAll 获取命名空间下的所有缓存项
//
// name 命名空间
func (c *Cache) GetAll(name string) (*map[string]any, bool) {
	innerMap, ok := c.cacheMap.Load(name)
	if !ok {
		return nil, false
	}
	inner, ok := innerMap.(*sync.Map)
	if !ok {
		return nil, false
	}

	var res map[string]any
	inner.Range(func(key, value any) bool {
		cacheVal, ok := value.(*cacheValue)
		if !ok {
			return true
		}
		// 懒删除
		if cacheVal.expireTime != nil && cacheVal.expireTime.Before(time.Now()) {
			inner.Delete(key)
		} else {
			res[key.(string)] = cacheVal.value
		}
		return true
	})

	return &res, true
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
	if cacheVal.expireTime != nil && cacheVal.expireTime.Before(time.Now()) {
		inner.Delete(key)
		return nil, false
	}

	return cacheVal.value, true
}

// Put 设置缓存项
//
// name 命名空间，key 键
func (c *Cache) Put(name string, key string, value any) {
	c.PutWithExpire(name, key, value)
}

// PutWithExpire 设置缓存项, 包含过期时间
func (c *Cache) PutWithExpire(name string, key string, value any, expire ...time.Duration) {
	inner, _ := c.cacheMap.LoadOrStore(name, &sync.Map{})
	innerMap, ok := inner.(*sync.Map)
	if !ok {
		return
	}

	var expireTime *time.Time
	if len(expire) != 0 {
		et := time.Now().Add(expire[0])
		expireTime = &et
	} else {
		et := time.Now().Add(c.expire)
		expireTime = &et
	}
	innerMap.Store(key, &cacheValue{
		value:      value,
		expireTime: expireTime,
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

			if cacheVal.expireTime != nil && cacheVal.expireTime.Before(time.Now()) {
				innerMap.Delete(key)
			}
			return true
		})
		return true
	})
}
