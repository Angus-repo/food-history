package com.example.foodhistory.controller;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/foods")
public class FoodController {
    
    @Autowired
    private FoodService foodService;
    
    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        List<Food> foods = foodService.searchFoods(keyword);
        List<String> base64Images = foods.stream()
                .map(food -> food.getImage() != null ? "data:" + food.getImageContentType() + ";base64," + Base64.getEncoder().encodeToString(food.getImage()) : null)
                .collect(Collectors.toList());
        model.addAttribute("foods", foods);
        model.addAttribute("base64Images", base64Images);
        model.addAttribute("keyword", keyword);
        return "food/list";
    }
    
    @GetMapping("/new")
    public String createForm(Model model) {
        Food food = new Food();
        model.addAttribute("food", food);
        model.addAttribute("imageUrl", null);
        return "food/form";
    }
    
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Food food = foodService.getFoodById(id);
        if (food == null) {
            return "redirect:/foods";
        }
        if (food.getImage() != null) {
            model.addAttribute("imageUrl", "data:" + food.getImageContentType() + ";base64," + Base64.getEncoder().encodeToString(food.getImage()));
        }
        model.addAttribute("food", food);
        return "food/form";
    }
    
    @PostMapping
    public String save(@ModelAttribute Food food, 
                      @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                      RedirectAttributes redirectAttributes) {
        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                String contentType = imageFile.getContentType();
                if (contentType != null && (
                    contentType.equals(MediaType.IMAGE_JPEG_VALUE) ||
                    contentType.equals(MediaType.IMAGE_PNG_VALUE) ||
                    contentType.equals(MediaType.IMAGE_GIF_VALUE))) {
                    
                    food.setImage(imageFile.getBytes());
                    food.setImageContentType(contentType);
                } else {
                    redirectAttributes.addFlashAttribute("error", "只支援 JPEG、PNG 或 GIF 格式的圖片");
                    return "redirect:/foods" + (food.getId() != null ? "/" + food.getId() + "/edit" : "/new");
                }
            } else if (food.getId() != null) {
                // 如果是編輯模式且沒有上傳新圖片，保留原有圖片
                Food existingFood = foodService.getFoodById(food.getId());
                if (existingFood != null) {
                    food.setImage(existingFood.getImage());
                    food.setImageContentType(existingFood.getImageContentType());
                }
            }
            
            foodService.saveFood(food);
            redirectAttributes.addFlashAttribute("success", "食物資料已成功儲存");
            return "redirect:/foods";
            
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "圖片處理失敗，請稍後再試");
            return "redirect:/foods" + (food.getId() != null ? "/" + food.getId() + "/edit" : "/new");
        }
    }
    
    @DeleteMapping("/{id}")
    @ResponseBody
    public void delete(@PathVariable Long id) {
        foodService.deleteFood(id);
    }
}