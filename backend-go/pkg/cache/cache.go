package cache

import (
	"backend-go/config"
	"sync"
	"time"
)

type Cache interface {
	GetAll(name string) (*map[string]any, bool)
	Get(name string, key string) (any, bool)
	Put(name string, key string, value any)
	PutWithExpire(name string, key string, value any, expire ...time.Duration)
	Delete(name string, key string)
	DeleteName(name string)
	Clear()
}

type InMemoryCache struct {
	cacheMap sync.Map
	expire   time.Duration
}

type cacheValue struct {
	value      any
	expireTime *time.Time
}

func NewInMemoryCache(cfg *config.CacheConfig) Cache {
	cache := &InMemoryCache{
		cacheMap: sync.Map{},
		expire:   time.Duration(cfg.Expire) * time.Second,
	}

	go func() {
		ticker := time.NewTicker(time.Hour * 24)
		defer ticker.Stop()
		for range ticker.C {
			cache.clearExpired()
		}
	}()

	return cache
}

func (c *InMemoryCache) GetAll(name string) (*map[string]any, bool) {
	innerMap, ok := c.cacheMap.Load(name)
	if !ok {
		return nil, false
	}
	inner, ok := innerMap.(*sync.Map)
	if !ok {
		return nil, false
	}

	res := make(map[string]any)
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

func (c *InMemoryCache) Get(name string, key string) (any, bool) {
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

func (c *InMemoryCache) Put(name string, key string, value any) {
	c.PutWithExpire(name, key, value)
}

func (c *InMemoryCache) PutWithExpire(name string, key string, value any, expire ...time.Duration) {
	inner, _ := c.cacheMap.LoadOrStore(name, &sync.Map{})
	innerMap, ok := inner.(*sync.Map)
	if !ok {
		return
	}

	var expireTime *time.Time
	if len(expire) != 0 {
		expireTime = new(time.Now().Add(expire[0]))
	} else {
		expireTime = new(time.Now().Add(c.expire))
	}
	innerMap.Store(key, &cacheValue{
		value:      value,
		expireTime: expireTime,
	})
}

func (c *InMemoryCache) Delete(name string, key string) {
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

func (c *InMemoryCache) DeleteName(name string) {
	c.cacheMap.Delete(name)
}

func (c *InMemoryCache) Clear() {
	c.cacheMap.Clear()
}

func (c *InMemoryCache) clearExpired() {
	c.cacheMap.Range(func(nsKey, nsValue any) bool {
		innerMap, ok := nsValue.(*sync.Map)
		if !ok {
			return true
		}

		innerMap.Range(func(key, value any) bool {
			cacheVal, ok := value.(*cacheValue)
			if !ok {
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
