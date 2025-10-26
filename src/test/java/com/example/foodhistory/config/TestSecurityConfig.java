package com.example.foodhistory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test-only Security Configuration
 * Completely disables authentication for UI tests to allow Selenium navigation
 * Uses highest priority order to override main SecurityConfig
 */
@Configuration
@EnableWebSecurity
@Profile("test")
@Order(1)  // Highest priority to override main SecurityConfig
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        // 完全禁用所有 security 功能
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()  // 允許所有請求
            )
            .csrf(csrf -> csrf.disable())  // 禁用 CSRF
            .headers(headers -> headers.disable())  // 禁用 headers
            .logout(logout -> logout.disable())  // 禁用登出
            .httpBasic(basic -> basic.disable())  // 禁用 HTTP Basic
            .formLogin(form -> form.disable())  // 禁用表單登入
            .anonymous(anonymous -> anonymous.disable());  // 禁用匿名用戶
        
        return http.build();
    }
    
    /**
     * Provide PasswordEncoder bean for test profile
     * Required by AuthController and other components
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
