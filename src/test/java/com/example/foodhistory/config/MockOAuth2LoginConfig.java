package com.example.foodhistory.config;

import com.example.foodhistory.model.User;
import com.example.foodhistory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;

/**
 * Mock OAuth2 登入配置
 * 為 UI BDD 測試提供預設的已認證使用者
 */
@TestConfiguration
@Profile("test")
public class MockOAuth2LoginConfig {

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    /**
     * 初始化測試用戶
     */
    @PostConstruct
    public void initTestUsers() {
        if (userRepository != null && passwordEncoder != null) {
            // 檢查測試用戶是否已存在
            Optional<User> existingUser = userRepository.findByEmail("test@example.com");
            
            if (existingUser.isEmpty()) {
                // 創建測試用戶
                User testUser = new User();
                testUser.setUsername("Test User");
                testUser.setEmail("test@example.com");
                testUser.setEncryptedPassword(passwordEncoder.encode("test123"));
                testUser.setRole("USER");
                testUser.setEnabled(true);
                testUser.setIsAuthorized(true);
                
                userRepository.save(testUser);
                System.out.println("✅ 已創建測試用戶: test@example.com");
            }
        }
    }

    /**
     * 創建一個 Mock 的 OAuth2 用戶
     */
    @Bean
    public OidcUser mockOidcUser() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-user-id");
        claims.put("name", "Test User");
        claims.put("email", "test@example.com");
        claims.put("email_verified", true);
        
        OidcIdToken idToken = new OidcIdToken(
            "mock-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );
        
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_openid"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_profile"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_email"));
        
        return new DefaultOidcUser(authorities, idToken, userInfo);
    }
}
