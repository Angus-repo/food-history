package com.example.foodhistory.controller;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Controller
@RequestMapping("/foods")
public class FoodController {
    
    @Autowired
    private FoodService foodService;
    
    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("foods", foodService.searchFoods(keyword));
        model.addAttribute("keyword", keyword);
        return "food/list";
    }
    
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("food", new Food());
        return "food/form";
    }
    
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("food", foodService.getFoodById(id));
        return "food/form";
    }
    
    @PostMapping
    public String save(@ModelAttribute Food food, @RequestParam(required = false) MultipartFile imageFile) {
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                food.setImage(imageFile.getBytes());
                food.setImageContentType(imageFile.getContentType());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        foodService.saveFood(food);
        return "redirect:/foods";
    }
    
    @DeleteMapping("/{id}")
    @ResponseBody
    public void delete(@PathVariable Long id) {
        foodService.deleteFood(id);
    }
}