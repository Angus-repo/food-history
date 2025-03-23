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
    
    @Column(name = "serving_size")
    private String servingSize;
    
    @Column(name = "carb_grams")
    private Integer carbGrams;
    
    @Column(length = 100)
    private String notes;
    
    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] image;
    
    @Column(name = "image_content_type")
    private String imageContentType;
}