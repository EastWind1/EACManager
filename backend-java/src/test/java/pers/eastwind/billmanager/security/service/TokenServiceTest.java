package pers.eastwind.billmanager.security.service;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pers.eastwind.billmanager.common.cache.CacheService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    private final Map<String, String> flagStore = new HashMap<>();
    private TokenService service;

    @BeforeEach
    void setUp() {
        CacheService cacheService = mock(CacheService.class);

        when(cacheService.hasFlag(eq(TokenService.BLACKLIST_CACHE), anyString()))
                .thenAnswer(invocation -> flagStore.containsKey(invocation.getArgument(1)));
        doAnswer(invocation -> {
            flagStore.put(invocation.getArgument(1), "1");
            return null;
        }).when(cacheService).setFlag(eq(TokenService.BLACKLIST_CACHE), anyString(), any(Duration.class));

        JWTProperties properties = new JWTProperties();
        properties.setSecret("test-only-jwt-key-4f6c1d2a9b8e7c3d5a1f0e9d8c7b6a5f");
        properties.setExpire(60);
        service = new TokenService(properties, cacheService);
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

        assertTrue(flagStore.containsKey(jti));
        assertThrows(BizException.class, () -> service.verify(token));
    }
}
