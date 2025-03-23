package com.example.foodhistory.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.service.FoodService;

public class FoodControllerTest {

    @Mock
    private FoodService foodService;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private MultipartFile imageFile;

    @InjectMocks
    private FoodController foodController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testList() {
        when(foodService.searchFoods(null)).thenReturn(Collections.emptyList());
        String viewName = foodController.list(null, model);
        assertEquals("food/list", viewName);
        verify(model).addAttribute("foods", Collections.emptyList());
    }

    @Test
    public void testCreateForm() {
        String viewName = foodController.createForm(model);
        assertEquals("food/form", viewName);
        verify(model).addAttribute(eq("food"), any(Food.class));
    }

    @Test
    public void testEditForm() {
        Food food = new Food();
        when(foodService.getFoodById(1L)).thenReturn(food);
        String viewName = foodController.editForm(1L, model);
        assertEquals("food/form", viewName);
        verify(model).addAttribute("food", food);
    }

    @Test
    public void testSave() throws Exception {
        Food food = new Food();
        when(imageFile.isEmpty()).thenReturn(true);
        String viewName = foodController.save(food, imageFile, redirectAttributes);
        assertEquals("redirect:/foods", viewName);
        verify(foodService).saveFood(food);
    }

    @Test
    public void testDelete() {
        doNothing().when(foodService).deleteFood(1L);
        foodController.delete(1L);
        verify(foodService).deleteFood(1L);
    }
}
