package pers.eastwind.billmanager.user.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pers.eastwind.billmanager.common.model.Result;
import pers.eastwind.billmanager.user.filter.JWTTokenFilter;
import pers.eastwind.billmanager.user.service.UserService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security配置类
 *
 * <p>自定义UserDetailService由 {@link UserService} 实现
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JWTTokenFilter jwtTokenFilter;
    private final JsonMapper jsonMapper;

    public SecurityConfig(JWTTokenFilter jwtTokenFilter, JsonMapper jsonMapper) {
        this.jwtTokenFilter = jwtTokenFilter;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/user/token").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(configurer -> {
                    // security 绕开了异常处理，需手动处理并指定编码
                    // 未认证
                    configurer.authenticationEntryPoint((request, response, authException) -> {
                        Result<Object> res = Result.error("未登录");
                        response.setCharacterEncoding("UTF-8");
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write(jsonMapper.writeValueAsString(res));
                    });
                    // 无权限
                    configurer.accessDeniedHandler((request, response, accessDeniedException) -> {
                        Result<Object> res = Result.error("无权限");
                        response.setCharacterEncoding("UTF-8");
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write(jsonMapper.writeValueAsString(res));
                    });
                })
                // 禁用 Session
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 添加JWT验证过滤器
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
