package pers.eastwind.billmanager.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Duration;

/**
 * 业务层唯一的缓存入口。
 * <p>
 * 声明式缓存（@Cacheable / @CacheEvict）仍然使用 Spring Cache；按 key 读写、清理以及
 * 登出黑名单这类标记位统一收敛在这里，业务层不再直接依赖 RedisTemplate / CacheManager。
 * 缓存命名空间由各业务模块自行声明，这里不感知具体业务名。
 */
@Component
public class CacheService {
    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    public CacheService(CacheManager cacheManager, StringRedisTemplate redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    public <T> T get(String name, Object key, Class<T> type) {
        Cache cache = cacheManager.getCache(name);
        return cache == null ? null : cache.get(key, type);
    }

    public void put(String name, Object key, Object value) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    /**
     * 按条目指定 TTL，覆盖缓存配置里的默认有效期；只支持 Redis 缓存。
     */
    public void put(String name, Object key, Object value, Duration ttl) {
        if (value == null) {
            throw new IllegalArgumentException("带 TTL 的缓存写入不支持 null 值");
        }
        Cache cache = cacheManager.getCache(name);
        if (!(cache instanceof RedisCache redisCache)) {
            throw new IllegalStateException("缓存 " + name + " 不是 Redis 缓存，无法指定 TTL");
        }
        RedisCacheConfiguration config = redisCache.getCacheConfiguration();
        byte[] cacheKey = bytes(config.getKeySerializationPair().write(entryKey(redisCache, config, key)));
        byte[] cacheValue = bytes(config.getValueSerializationPair().write(value));
        redisCache.getNativeCache().put(name, cacheKey, cacheValue, ttl);
    }

    public void evict(String name, Object key) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.evict(key);
        }
    }

    public void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * 只关心是否存在的短标记，例如登出后 token 的 jti。
     */
    public boolean hasFlag(String name, String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(flagKey(name, key)));
    }

    public void setFlag(String name, String key, Duration ttl) {
        redisTemplate.opsForValue().set(flagKey(name, key), "1", ttl);
    }

    private static String entryKey(RedisCache cache, RedisCacheConfiguration config, Object key) {
        String converted = convertKey(config, key);
        return config.usePrefix() ? config.getKeyPrefixFor(cache.getName()) + converted : converted;
    }

    private static String convertKey(RedisCacheConfiguration config, Object key) {
        if (key instanceof String stringKey) {
            return stringKey;
        }
        ConversionService conversionService = config.getConversionService();
        return conversionService == null ? key.toString() : conversionService.convert(key, String.class);
    }

    private static String flagKey(String name, String key) {
        return name + "::" + key;
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer source = buffer.duplicate();
        byte[] bytes = new byte[source.remaining()];
        source.get(bytes);
        return bytes;
    }
}
