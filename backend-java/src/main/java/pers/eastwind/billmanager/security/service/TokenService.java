package pers.eastwind.billmanager.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.cache.CacheService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.security.config.JWTProperties;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 单 token：JWT 的 subject 是用户 id，权限由用户权限缓存组装。
 *
 * <p>登出只把 jti 写入缓存黑名单，直到 token 自然过期。</p>
 */
@Service
public class TokenService {
    static final String BLACKLIST_CACHE = "expire_token";

    private final JWTProperties properties;
    private final CacheService cacheService;

    public TokenService(JWTProperties properties, CacheService cacheService) {
        this.properties = properties;
        this.cacheService = cacheService;
    }

    public String issue(Integer userId) {
        Instant now = Instant.now();
        Algorithm algorithm = Algorithm.HMAC256(properties.getSecret());
        try {
            return JWT.create()
                    .withSubject(String.valueOf(userId))
                    .withJWTId(UUID.randomUUID().toString())
                    .withIssuedAt(now)
                    .withExpiresAt(now.plus(properties.getExpire(), ChronoUnit.SECONDS))
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            throw new BizException("JWT 签名失败", e);
        }
    }

    public DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("Token 无效");
        }
        DecodedJWT jwt;
        try {
            jwt = JWT.require(Algorithm.HMAC256(properties.getSecret())).build().verify(token);
        } catch (JWTVerificationException e) {
            throw new BizException("验证 JWT 异常", e);
        }
        String id = jwt.getId();
        if (id == null || id.isBlank()) {
            throw new BizException("Token 无效");
        }
        if (cacheService.hasFlag(BLACKLIST_CACHE, id)) {
            throw new BizException("Token 已失效");
        }
        return jwt;
    }

    public void expire(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        DecodedJWT jwt;
        try {
            jwt = JWT.require(Algorithm.HMAC256(properties.getSecret())).build().verify(token);
        } catch (JWTVerificationException e) {
            return;
        }
        long ttl = remainingSeconds(jwt);
        if (ttl <= 0 || jwt.getId() == null || jwt.getId().isBlank()) {
            return;
        }
        cacheService.setFlag(BLACKLIST_CACHE, jwt.getId(), Duration.ofSeconds(ttl));
    }

    private static long remainingSeconds(DecodedJWT jwt) {
        Instant expiresAt = jwt.getExpiresAtAsInstant();
        if (expiresAt == null) {
            return 0;
        }
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }
}
