package com.example.foodhistory.config;

import com.example.foodhistory.repository.UserRepository;
import com.example.foodhistory.repository.PersistentLoginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

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

    @Lazy
    @Autowired(required = false)
    private PersistentTokenRepository persistentTokenRepository;

    @Lazy
    @Autowired(required = false)
    private UserDetailsService userDetailsService;

    @Lazy
    @Autowired(required = false)
    private PersistentLoginRepository persistentLoginRepository;

    private static final SecureRandom random = new SecureRandom();

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
        
        // 手動建立 Remember Me token（用於 OAuth2 登入）
        if (persistentTokenRepository != null && userDetailsService != null) {
            try {
                // 對於 OAuth2 登入，使用 email 作為 username（因為 authentication.getName() 是 OAuth2 subject ID）
                String usernameForRememberMe = null;
                if (authentication instanceof OAuth2AuthenticationToken) {
                    OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                    if (oauth2Token.getPrincipal() instanceof OidcUser) {
                        OidcUser oidcUser = (OidcUser) oauth2Token.getPrincipal();
                        usernameForRememberMe = oidcUser.getAttribute("email");
                    }
                } else {
                    usernameForRememberMe = authentication.getName();
                }
                
                if (usernameForRememberMe != null) {
                    // 驗證使用者存在
                    userDetailsService.loadUserByUsername(usernameForRememberMe);
                    
                    // 建立 persistent token
                    String series = generateSeriesData();
                    String token = generateTokenData();
                    
                    PersistentRememberMeToken persistentToken = new PersistentRememberMeToken(
                        usernameForRememberMe, // 使用 email 作為 username
                        series,
                        token,
                        new Date()
                    );
                    
                    persistentTokenRepository.createNewToken(persistentToken);
                    
                    // 儲存 IP 位址到資料庫（需要自訂實作）
                    saveIpAddress(series, request.getRemoteAddr());
                    
                    // 設定 Remember Me cookie
                    setCookie(new String[] {series, token}, 2592000, request, response);
                    
                    logger.info("Remember Me token created for user: {} from IP: {}", usernameForRememberMe, request.getRemoteAddr());
                }
            } catch (Exception e) {
                logger.error("Failed to create Remember Me token", e);
            }
        }
        
        // 繼續原本的成功處理邏輯（導向到 /foods）
        super.onAuthenticationSuccess(request, response, authentication);
    }
    
    private String generateSeriesData() {
        byte[] newSeries = new byte[16];
        random.nextBytes(newSeries);
        return Base64.getEncoder().encodeToString(newSeries);
    }
    
    private String generateTokenData() {
        byte[] newToken = new byte[16];
        random.nextBytes(newToken);
        return Base64.getEncoder().encodeToString(newToken);
    }
    
    private void setCookie(String[] tokens, int maxAge, HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = encodeCookie(tokens);
        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("food-history-remember-me", cookieValue);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
    
    private String encodeCookie(String[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            sb.append(tokens[i]);
            if (i < tokens.length - 1) {
                sb.append(":");
            }
        }
        return Base64.getEncoder().encodeToString(sb.toString().getBytes());
    }
    
    private void saveIpAddress(String series, String ipAddress) {
        if (persistentLoginRepository != null) {
            try {
                persistentLoginRepository.updateIpAddress(series, ipAddress);
            } catch (Exception e) {
                logger.error("Failed to save IP address for series: {}", series, e);
            }
        }
    }
}
