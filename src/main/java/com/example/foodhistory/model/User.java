package com.example.foodhistory.model;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(name = "encrypted_password", nullable = false)
    private String encryptedPassword;
    
    @Column(nullable = false)
    private String role;
    
    private boolean enabled = true;
}