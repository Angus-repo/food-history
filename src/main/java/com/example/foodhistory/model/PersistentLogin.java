package com.example.foodhistory.model;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "persistent_logins")
public class PersistentLogin {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "series", length = 64, unique = true, nullable = false)
    private String series;
    
    @Column(name = "username", length = 64, nullable = false)
    private String username;
    
    @Column(name = "token", length = 64, nullable = false)
    private String token;
    
    @Column(name = "last_used", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUsed;
    
    @Column(name = "ip", length = 45)
    private String ip;
}
