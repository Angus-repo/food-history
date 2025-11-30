package com.example.foodhistory.service;

import com.example.foodhistory.model.User;
import com.example.foodhistory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username)) // 嘗試用 email 查找（OAuth2 使用者）
                .orElseThrow(() -> new UsernameNotFoundException("使用者不存在: " + username));

        // 對於 OAuth2 使用者（沒有本地密碼的），使用 email 作為 username
        // 這樣 Remember Me 機制才能正確匹配（因為 OAuth2 登入時存的是 email）
        String effectiveUsername = (user.getEmail() != null && 
                                    (user.getEncryptedPassword() == null || user.getEncryptedPassword().isEmpty()))
                                   ? user.getEmail() 
                                   : user.getUsername();
        
        // 對於 OAuth2 使用者，密碼可能為空，使用空字串避免 NullPointerException
        String password = user.getEncryptedPassword() != null ? user.getEncryptedPassword() : "";

        return new org.springframework.security.core.userdetails.User(
                effectiveUsername,
                password,
                user.isEnabled(),
                true,
                true,
                true,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}