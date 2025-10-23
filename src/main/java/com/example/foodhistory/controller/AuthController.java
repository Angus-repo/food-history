package com.example.foodhistory.controller;

import com.example.foodhistory.model.User;
import com.example.foodhistory.repository.UserRepository;
import com.example.foodhistory.controller.dto.UserRegistrationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationDto dto) {
        // basic validation
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username is required");
        }

        if (!isPasswordValid(dto.getPassword())) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters and contain letters and digits");
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            return ResponseEntity.badRequest().body("Password and confirmation do not match");
        }

        Optional<User> existing = userRepository.findByUsername(dto.getUsername());
        if (existing.isPresent()) {
            return ResponseEntity.status(409).body("Username already exists");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEncryptedPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole("USER");
        user.setEnabled(true);
        userRepository.save(user);

        return ResponseEntity.ok("User registered");
    }

    private boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
            if (hasLetter && hasDigit) return true;
        }
        return false;
    }
}
