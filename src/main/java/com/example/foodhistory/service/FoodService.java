package com.example.foodhistory.service;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    public Page<Food> searchFoods(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return foodRepository.findAll(pageable);
        }
        return foodRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
    }
    
    public Food getFoodById(Long id) {
        return foodRepository.findById(id).orElse(null);
    }
    
    public Food saveFood(Food food) {
        return foodRepository.save(food);
    }
    
    public void deleteFood(Long id) {
        // 先檢查是否存在，避免刪除不存在的食物時拋出異常
        if (foodRepository.existsById(id)) {
            foodRepository.deleteById(id);
        }
        // 如果不存在也不報錯，符合冪等性原則
    }
    
    public Food toggleFavorite(Long id) {
        Food food = getFoodById(id);
        if (food != null) {
            food.setIsFavorite(!food.getIsFavorite());
            return saveFood(food);
        }
        return null;
    }
    
    public List<Food> getFavoriteRecommendations(int limit) {
        List<Food> favorites = foodRepository.findByIsFavoriteTrueOrderByNameAsc();
        return favorites.size() > limit ? favorites.subList(0, limit) : favorites;
    }
    
    public List<Food> getAllFavorites() {
        return foodRepository.findByIsFavoriteTrueOrderByNameAsc();
    }
}