package com.example.foodhistory.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(authz -> authz
                .anyRequest().permitAll()  // 允許所有請求，無需認證
            )
            .csrf(csrf -> csrf.disable())  // 暫時禁用 CSRF 保護
            .headers(headers -> headers
                .frameOptions().sameOrigin()
            );

        return http.build();
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return new OAuth2UserService<OidcUserRequest, OidcUser>() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                OidcUser oidcUser = (OidcUser) delegate.loadUser(userRequest);

                String email = oidcUser.getEmail();
                String adminEmail = System.getenv("ADMIN_EMAIL");

                Set<GrantedAuthority> updatedAuthorities = new HashSet<>(oidcUser.getAuthorities());
                if (adminEmail != null && email.equals(adminEmail)) {
                    updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                return new DefaultOidcUser(updatedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }
}