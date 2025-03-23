package com.example.foodhistory.service;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class FoodServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private FoodService foodService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAllFoods() {
        when(foodRepository.findAll()).thenReturn(Collections.emptyList());
        List<Food> foods = foodService.getAllFoods();
        assertEquals(Collections.emptyList(), foods);
    }

    @Test
    public void testSearchFoods() {
        when(foodRepository.findByNameContainingIgnoreCase("keyword")).thenReturn(Collections.emptyList());
        List<Food> foods = foodService.searchFoods("keyword");
        assertEquals(Collections.emptyList(), foods);
    }

    @Test
    public void testGetFoodById() {
        Food food = new Food();
        when(foodRepository.findById(1L)).thenReturn(Optional.of(food));
        Food result = foodService.getFoodById(1L);
        assertEquals(food, result);
    }

    @Test
    public void testSaveFood() {
        Food food = new Food();
        when(foodRepository.save(food)).thenReturn(food);
        Food result = foodService.saveFood(food);
        assertEquals(food, result);
    }

    @Test
    public void testDeleteFood() {
        doNothing().when(foodRepository).deleteById(1L);
        foodService.deleteFood(1L);
        verify(foodRepository).deleteById(1L);
    }
}
