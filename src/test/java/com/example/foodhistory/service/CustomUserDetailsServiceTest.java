package com.example.foodhistory.service;

import com.example.foodhistory.model.User;
import com.example.foodhistory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testLoadUserByUsername() {
        User user = new User();
        user.setUsername("user");
        user.setEncryptedPassword("password");
        user.setRole("USER");
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("user");
        // 有密碼的使用者應該使用 username
        assertEquals("user", userDetails.getUsername());
    }

    @Test
    public void testLoadUserByUsernameNotFound() {
        when(userRepository.findByUsername("user")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername("user"));
    }

    @Test
    public void testLoadOAuth2UserByEmail() {
        // OAuth2 使用者沒有本地密碼，應該使用 email 作為 username
        User oauthUser = new User();
        oauthUser.setUsername("OAuth User");
        oauthUser.setEmail("oauth@example.com");
        oauthUser.setEncryptedPassword(""); // OAuth2 使用者沒有密碼
        oauthUser.setRole("USER");
        oauthUser.setEnabled(true);
        
        when(userRepository.findByUsername("oauth@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(oauthUser));
        
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("oauth@example.com");
        // OAuth2 使用者（沒有密碼）應該使用 email 作為 username
        assertEquals("oauth@example.com", userDetails.getUsername());
    }

    @Test
    public void testLoadUserWithBothPasswordAndEmail() {
        // 既有本地密碼又有 email 的使用者應該使用 username
        User user = new User();
        user.setUsername("localuser");
        user.setEmail("local@example.com");
        user.setEncryptedPassword("hashedpassword");
        user.setRole("USER");
        user.setEnabled(true);
        
        when(userRepository.findByUsername("localuser")).thenReturn(Optional.of(user));
        
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("localuser");
        // 有密碼的使用者應該使用 username
        assertEquals("localuser", userDetails.getUsername());
    }
}
