package com.hfwas.devops.config;

import com.hfwas.devops.handler.CustomAuthenticationSuccessHandler;
import com.hfwas.devops.mapper.DevopsSessionMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * @author hfwas
 * @package com.hfwas.devops.config
 * @date 2025/3/20
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Resource
    private DevopsSessionMapper devopsSessionMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2AuthorizedClientService authorizedClientService) throws Exception {
        //
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/oauth/*")
                .permitAll()
                .anyRequest()
                .authenticated()
        );
        http.oauth2Login(oauth2 -> oauth2
                .successHandler(new CustomAuthenticationSuccessHandler(authorizedClientService, devopsSessionMapper))
        );
        return http.build();
    }

    @Bean
    public DefaultOAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        return authorizedClientManager;
    }

}
