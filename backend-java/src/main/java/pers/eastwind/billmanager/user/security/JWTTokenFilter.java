package pers.eastwind.billmanager.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.user.config.UserProperties;
import pers.eastwind.billmanager.user.model.User;
import pers.eastwind.billmanager.user.service.UserService;
import pers.eastwind.billmanager.user.util.JWTUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * JWT 过滤器
 * 替代 Spring Security 默认的 Session 认证
 */
@Slf4j
@Component
public class JWTTokenFilter extends OncePerRequestFilter {
    private final UserService userService;
    private final UserProperties properties;

    public JWTTokenFilter(UserService userService, UserProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 获取 token
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("X-Auth-Token")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token != null) {
            String userName = null;
            try {
                var jwt = JWTUtil.verifyToken(properties.getKey(), token);
                String host = request.getHeader(HttpHeaders.HOST);
                if (Objects.equals(host, jwt.getSubject())) {
                    userName = jwt.getAudience().getFirst();
                }
            } catch (BizException _) {
                log.error("JWT 解析失败: {}", token);
            }
            if (userName != null) {
                User user = userService.loadUserByUsername(userName);
                if (user.isDisabled()) {
                    // 登录时已验证，此处仅记录日志
                    log.error("用户被禁用: {}", userName);
                } else {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, user.getPassword(), user.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
