package pers.eastwind.billmanager.config;

import pers.eastwind.billmanager.model.dto.Result;
import pers.eastwind.billmanager.filter.JWTTokenFilter;
import pers.eastwind.billmanager.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security配置类
 *
 * <p>自定义UserDetailService由 {@link UserService} 实现
 */
@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JWTTokenFilter jwtTokenFilter;

    public SecurityConfig(ObjectMapper objectMapper, JWTTokenFilter jwtTokenFilter) {
        this.objectMapper = objectMapper;
        this.jwtTokenFilter = jwtTokenFilter;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/user/token").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                // 禁用 Session
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 添加JWT验证过滤器
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class)
                // 自定义认证失败处理器
                .exceptionHandling(configurer -> {
                    // 未认证
                    configurer.authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(objectMapper.writeValueAsString(Result.error("未认证")));
                    });
                    // 权限不足
                    configurer.accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(objectMapper.writeValueAsString(Result.error("权限不足")));
                    });
                });
        return http.build();
    }

}
