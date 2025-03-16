package com.example.foodhistory.service;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FoodService {
    @Autowired
    private FoodRepository foodRepository;
    
    public List<Food> getAllFoods() {
        return foodRepository.findAll();
    }
    
    public List<Food> searchFoods(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return foodRepository.findAll();
        }
        return foodRepository.findByNameContainingIgnoreCase(keyword.trim());
    }
    
    public Food getFoodById(Long id) {
        return foodRepository.findById(id).orElse(null);
    }
    
    public Food saveFood(Food food) {
        return foodRepository.save(food);
    }
    
    public void deleteFood(Long id) {
        foodRepository.deleteById(id);
    }
}