package com.hfwas.devops.config;

import com.hfwas.devops.common.core.exception.ApiErrorWriter;
import com.hfwas.devops.common.core.requestid.RequestIdFilter;
import com.hfwas.devops.common.error.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;
    private final RequestIdFilter requestIdFilter;
    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;
    private final ApiErrorWriter apiErrorWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health/check").permitAll()
                        .requestMatchers("/internal/keycloak/events").permitAll()
                        // Tekton 任务回传 SBOM 等产物（集群内无用户 JWT）
                        .requestMatchers(HttpMethod.POST, "/pipeline/runs/*/artifacts").permitAll()
                        .requestMatchers("/ws/exec/**").permitAll()
                        .requestMatchers("/ws/container/**").permitAll()
                        .requestMatchers("/container/**").authenticated()
                        .requestMatchers("/user/users/page", "/user/users/save", "/user/users/delete").hasRole("admin")
                        .requestMatchers("/user/login-logs/**").hasRole("admin")
                        .requestMatchers("/user/oper-logs/**").hasRole("admin")
                        .requestMatchers("/user/integrations/**").hasRole("admin")
                        .requestMatchers("/user/messages/admin/**").hasRole("admin")
                        .requestMatchers("/user/message-notify/**").hasRole("admin")
                        .requestMatchers("/user/tenants/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                apiErrorWriter.write(response, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN)))
                .addFilterBefore(requestIdFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
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
