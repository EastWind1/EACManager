package pers.eastwind.billmanager.security.service;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.security.config.JWTProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    private final Map<String, String> redisStore = new HashMap<>();
    private TokenService service;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString()))
                .thenAnswer(invocation -> redisStore.containsKey(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        JWTProperties properties = new JWTProperties();
        properties.setSecret("test-only-jwt-key-4f6c1d2a9b8e7c3d5a1f0e9d8c7b6a5f");
        properties.setExpire(60);
        service = new TokenService(properties, redisTemplate);
    }

    @Test
    void shouldUseUserIdAsSubject() {
        String token = service.issue(42);

        var jwt = service.verify(token);

        assertEquals("42", jwt.getSubject());
        assertEquals(null, jwt.getClaim("roles").asList(String.class));
    }

    @Test
    void shouldRejectTokenAfterLogout() {
        String token = service.issue(42);
        String jti = JWT.decode(token).getId();

        service.expire(token);

        assertTrue(redisStore.containsKey("expire_token::" + jti));
        assertThrows(BizException.class, () -> service.verify(token));
    }
}
