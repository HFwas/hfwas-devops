package com.hfwas.devops.config;

import com.hfwas.devops.common.core.exception.ApiErrorWriter;
import com.hfwas.devops.common.core.requestid.RequestIdFilter;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.user.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;
    private final RequestIdFilter requestIdFilter;
    private final ApiErrorWriter apiErrorWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Stateless JWT API: no browser cookie session; CSRF not applicable. Keep filter enabled but skip token check.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health/check").permitAll()
                        .requestMatchers("/user/auth/login").permitAll()
                        .requestMatchers("/user/users/page", "/user/users/save", "/user/users/delete").hasRole("admin")
                        .requestMatchers("/user/sessions/**").hasRole("admin")
                        .requestMatchers("/user/login-logs/**").hasRole("admin")
                        .requestMatchers("/user/oper-logs/**").hasRole("admin")
                        .requestMatchers("/user/integrations/**").hasRole("admin")
                        .requestMatchers("/user/messages/admin/**").hasRole("admin")
                        .requestMatchers("/user/message-notify/**").hasRole("admin")
                        .requestMatchers("/user/tenants/**").hasRole("admin")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                apiErrorWriter.write(response, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN)))
                // 过滤器顺序: RequestIdFilter → JwtAuthFilter → TenantContextFilter → UsernamePasswordAuthenticationFilter
                // 注意: JwtAuthFilter 必须先添加到链中才能被其他 addFilterBefore/addFilterAfter 引用
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter, JwtAuthFilter.class)
                .addFilterAfter(tenantContextFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Vite may bind 5173+ when ports are busy; proxy forwards Origin, so patterns must cover them.
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
