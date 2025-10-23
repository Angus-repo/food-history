package com.example.foodhistory.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import com.example.foodhistory.service.CustomUserDetailsService;
import com.example.foodhistory.repository.UserRepository;
import com.example.foodhistory.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    // optional: only present when OAuth client registrations are configured
    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Value("${app.admin.email:}")
    private String adminEmailProperty;

    // OAuth users will always be merged into existing accounts that match by email

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(authz -> authz
                .antMatchers("/css/**", "/js/**", "/icons/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/foods", true)
                .permitAll()
            );

        // Only enable OAuth2 login when a ClientRegistrationRepository is present
        if (clientRegistrationRepository != null) {
            // 如果 repository 是 InMemoryClientRegistrationRepository，列出所有 registration id 以方便 debug
            if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository) {
                InMemoryClientRegistrationRepository repo = (InMemoryClientRegistrationRepository) clientRegistrationRepository;
                StringBuilder ids = new StringBuilder();
                for (ClientRegistration reg : repo) {
                    if (ids.length() > 0) ids.append(", ");
                    ids.append(reg.getRegistrationId());
                }
                logger.info("Configured OAuth2 client registrations: {}", ids.toString());
            }
            http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oAuth2LoginSuccessHandler)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService())
                )
            );
        } else {
            logger.warn("OAuth2 client registrations not found. Google login will be disabled until client-id/secret are configured.");
        }

        http
            .logout(logout -> logout.permitAll())
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions().sameOrigin());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return new OAuth2UserService<OidcUserRequest, OidcUser>() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                OidcUser oidcUser = delegate.loadUser(userRequest);

                String email = oidcUser.getAttribute("email");
                String name = oidcUser.getAttribute("name");

                // 判斷是否為管理員
                Set<GrantedAuthority> updatedAuthorities = new HashSet<>(oidcUser.getAuthorities());
                if (adminEmailProperty != null && !adminEmailProperty.isEmpty() && email != null && email.equals(adminEmailProperty)) {
                    updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                // 若 DB 中已有此 email 的 user，則總是合併：更新 username (若不同)、標記為已授權並保留 local 密碼
                if (email != null) {
                    userRepository.findByEmail(email).map(existing -> {
                        boolean changed = false;
                        if (name != null && !name.isEmpty() && (existing.getUsername() == null || !existing.getUsername().equals(name))) {
                            existing.setUsername(name);
                            changed = true;
                        }
                        if (!existing.isAuthorized()) {
                            existing.setIsAuthorized(true);
                            changed = true;
                        }
                        if (existing.getEmail() == null) {
                            existing.setEmail(email);
                            changed = true;
                        }
                        if (changed) userRepository.save(existing);
                        return existing;
                    }).orElseGet(() -> {
                        // DB 中沒有此 email -> 建立新 OAuth 使用者
                        User user = new User();
                        String username = (name != null && !name.isEmpty()) ? name : email.split("@")[0];
                        user.setUsername(username);
                        user.setEmail(email);
                        user.setRole("USER");
                        user.setEnabled(true);
                        user.setEncryptedPassword("");
                        user.setIsAuthorized(true);
                        User savedUser = userRepository.save(user);
                        logger.info("Created new OAuth user: {}", email);
                        return savedUser;
                    });
                }

                return new DefaultOidcUser(updatedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }
}