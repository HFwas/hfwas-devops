package com.hfwas.devops.config;

import com.hfwas.devops.handler.CustomAuthenticationFailureHandler;
import com.hfwas.devops.handler.CustomAuthenticationSuccessHandler;
import com.hfwas.devops.mapper.DevopsSessionMapper;
import com.hfwas.devops.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * @author hfwas
 * @package com.hfwas.devops.config
 * @date 2025/3/20
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final DevopsSessionMapper devopsSessionMapper;

    public SecurityConfig(CustomUserDetailsService userDetailsService, DevopsSessionMapper devopsSessionMapper) {
        this.userDetailsService = userDetailsService;
        this.devopsSessionMapper = devopsSessionMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2AuthorizedClientService authorizedClientService) throws Exception {
        //
        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.sessionManagement(session ->
                session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false));
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/oauth/*")
                .permitAll()
                .anyRequest()
                .authenticated()
        );
        http.oauth2Login(oauth2 -> oauth2
                .successHandler(new CustomAuthenticationSuccessHandler(authorizedClientService, devopsSessionMapper))
                .failureHandler(new CustomAuthenticationFailureHandler())
        );
        http.formLogin(form -> form
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("http://localhost:3000", true)
                .failureUrl("/login?error")
                .permitAll());
        http.logout(logout -> logout.logoutUrl("/logout"));
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    // 👇 关键点：未登录时返回 401 JSON，而不是跳转
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\": \"未登录\"}");
                })
        );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3005")); // Allow your frontend origin
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS", "PUT", "DELETE"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
        configuration.setAllowCredentials(true); // If you need to send cookies or authorization headers
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS to all paths
        return source;
    }

    @Bean
    public DefaultOAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        return authorizedClientManager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

}
