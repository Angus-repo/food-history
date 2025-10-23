package com.example.foodhistory.config;

import com.example.foodhistory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private OAuth2AuthorizedClientService authorizedClientService;

    public OAuth2LoginSuccessHandler() {
        // 設定預設的成功導向 URL
        setDefaultTargetUrl("/foods");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            
            if (oauth2Token.getPrincipal() instanceof OidcUser) {
                OidcUser oidcUser = (OidcUser) oauth2Token.getPrincipal();
                String email = oidcUser.getAttribute("email");
                
                // 從 OAuth2AuthorizedClientService 取得 refresh token
                if (authorizedClientService != null && email != null) {
                    try {
                        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                            oauth2Token.getAuthorizedClientRegistrationId(),
                            oauth2Token.getName()
                        );
                        
                        if (authorizedClient != null) {
                            final String refreshToken;
                            if (authorizedClient.getRefreshToken() != null) {
                                refreshToken = authorizedClient.getRefreshToken().getTokenValue();
                            } else {
                                refreshToken = null;
                            }
                            
                            if (refreshToken != null) {
                                // 更新資料庫中的 refresh token
                                userRepository.findByEmail(email).ifPresent(user -> {
                                    if (!refreshToken.equals(user.getRefreshToken())) {
                                        user.setRefreshToken(refreshToken);
                                        userRepository.save(user);
                                        logger.info("Saved refresh token for user: {}", email);
                                    }
                                });
                            } else {
                                logger.warn("No refresh token available for user: {}. Make sure access_type=offline is configured.", email);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error saving refresh token for user: {}", email, e);
                    }
                }
            }
        }
        
        // 繼續原本的成功處理邏輯（導向到 /foods）
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
