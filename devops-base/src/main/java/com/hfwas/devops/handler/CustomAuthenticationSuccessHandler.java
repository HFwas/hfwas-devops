package com.hfwas.devops.handler;

/**
 * @author hfwas
 * @package com.hfwas.devops.handler
 * @date 2025/3/20
 */

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getCredentials() instanceof OAuth2AccessTokenResponse) {
            OAuth2AccessTokenResponse tokenResponse =
                    (OAuth2AccessTokenResponse) authentication.getCredentials();

            // 获取 Refresh Token
            OAuth2RefreshToken refreshToken = tokenResponse.getRefreshToken();
            if (refreshToken != null) {
                String refreshTokenValue = refreshToken.getTokenValue();
                // 保存或处理 refreshTokenValue
                System.out.println("Refresh Token: " + refreshTokenValue);
            }
        }

        // 重定向到默认成功页面或其他逻辑
        response.sendRedirect("/home");
    }
}
