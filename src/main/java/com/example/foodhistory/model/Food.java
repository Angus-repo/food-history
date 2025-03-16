package com.example.foodhistory.model;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "foods")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 30, nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String servingSize;
    
    @Column(nullable = false)
    private Integer grams;
    
    @Column(length = 100)
    private String notes;
    
    @Lob
    private byte[] image;
    
    private String imageContentType;
}