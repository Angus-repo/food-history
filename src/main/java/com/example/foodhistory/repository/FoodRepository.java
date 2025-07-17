package com.example.foodhistory.repository;

import com.example.foodhistory.model.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Long> {
    List<Food> findByNameContainingIgnoreCase(String keyword);
    Page<Food> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    List<Food> findByIsFavoriteTrueOrderByNameAsc();
    Page<Food> findByIsFavoriteTrueOrderByNameAsc(Pageable pageable);
}