package com.example.foodhistory.controller;

import com.example.foodhistory.model.User;
import com.example.foodhistory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/authorize")
    public ResponseEntity<String> authorizeUser(@PathVariable Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setIsAuthorized(true);
            userRepository.save(user);
            return ResponseEntity.ok("User authorized successfully.");
        } else {
            return ResponseEntity.status(404).body("User not found.");
        }
    }
}