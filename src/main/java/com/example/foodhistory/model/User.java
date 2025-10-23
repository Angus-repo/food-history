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
    
    @Column(unique = true)
    private String email;
    
    @Column(name = "encrypted_password", nullable = false)
    private String encryptedPassword;
    
    @Column(nullable = false)
    private String role;
    
    private boolean enabled = true;

    @Column(name = "is_authorized", nullable = false, columnDefinition = "boolean default false")
    private boolean isAuthorized = false;

    @Column(name = "refresh_token", length = 1000)
    private String refreshToken;

    public boolean isAuthorized() {
        return isAuthorized;
    }

    public void setIsAuthorized(boolean isAuthorized) {
        this.isAuthorized = isAuthorized;
    }
}