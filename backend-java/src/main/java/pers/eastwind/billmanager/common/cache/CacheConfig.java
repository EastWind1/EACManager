package pers.eastwind.billmanager.common.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class CacheConfig {
    /**
     * 只提供缓存序列化配置，CacheManager 交给 Spring Boot 自动配置，
     * 这样 spring.cache.redis.* 的 TTL / null 策略仍然生效。
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(cacheSerializer()));

        var redis = cacheProperties.getRedis();
        if (redis.getTimeToLive() != null) {
            config = config.entryTtl(redis.getTimeToLive());
        }
        if (!redis.isCacheNullValues()) {
            config = config.disableCachingNullValues();
        }
        return config;
    }

    /**
     * 缓存值需要带类型信息，否则 record / List 读回后会变成 LinkedHashMap。
     */
    private GenericJacksonJsonRedisSerializer cacheSerializer() {
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("pers.eastwind.billmanager.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubTypeIsArray()
                .build();
        JsonMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(validator, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY)
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }
}
