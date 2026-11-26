package pers.eastwind.billmanager.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.service.AuthenticateService;
import pers.eastwind.billmanager.security.service.TokenService;

import java.io.IOException;

/**
 * JWT 过滤器：替代 Spring Security 默认的 Session 认证
 */
@Slf4j
@Component
public class JWTTokenFilter extends OncePerRequestFilter {
    private final AuthenticateService authenticateService;
    private final TokenService tokenService;

    public JWTTokenFilter(AuthenticateService authenticateService, TokenService tokenService) {
        this.authenticateService = authenticateService;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
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
            AuthorityUser user = null;
            try {
                user = authenticateService.loadUserById(Integer.valueOf(tokenService.verify(token).getSubject()));
            } catch (BizException | UsernameNotFoundException | NumberFormatException e) {
                log.warn("Token 校验失败: {}", e.getMessage());
            }
            if (user != null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, user.getPassword(), user.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            }
        }
        filterChain.doFilter(request, response);
    }
}
