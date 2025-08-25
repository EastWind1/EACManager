package pers.eastwind.billmanager.filter;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pers.eastwind.billmanager.service.JWTService;
import pers.eastwind.billmanager.service.UserService;

import java.io.IOException;
import java.text.ParseException;

/**
 * JWT 过滤器
 * 替代 Spring Security 默认的 Session 认证
 */
@Slf4j
@Component
public class JWTTokenFilter extends OncePerRequestFilter {
    private final JWTService jwtUtil;
    private final UserService userService;

    public JWTTokenFilter(JWTService jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
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
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.verifyToken(token, request.getHeader(HttpHeaders.HOST))) {
            filterChain.doFilter(request, response);
            return;
        }
        String userName;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            userName = jwt.getJWTClaimsSet().getAudience().getFirst();
        } catch (ParseException e) {
            filterChain.doFilter(request, response);
            return;
        }
        if (userName != null) {
            UserDetails user = userService.loadUserByUsername(userName);
            if (user.isEnabled()) {
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
