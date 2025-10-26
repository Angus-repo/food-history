package com.example.foodhistory.controller;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.service.FoodService;
import com.example.foodhistory.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Controller
@RequestMapping("/foods")
public class FoodController {
    
    @Autowired
    private FoodService foodService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "12") int size,
                      HttpSession session,
                      Model model) {
        // 創建分頁請求，按ID降序排列（最新的在前面）
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Food> foodPage = foodService.searchFoods(keyword, pageable);
        
        // 處理搜尋歷史
        if (keyword != null && !keyword.trim().isEmpty()) {
            addToSearchHistory(session, keyword.trim());
        }
        
        // 獲取最愛食物推薦（前5個）
        List<Food> favoriteRecommendations = foodService.getFavoriteRecommendations(5);
        
        // 獲取近期搜尋推薦（前5個）
        List<String> allRecentSearches = getRecentSearches(session);
        List<String> recentSearches = allRecentSearches.size() > 5 ? 
            allRecentSearches.subList(0, 5) : allRecentSearches;
                
        model.addAttribute("foods", foodPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", foodPage.getTotalPages());
        model.addAttribute("totalElements", foodPage.getTotalElements());
        model.addAttribute("hasNext", foodPage.hasNext());
        model.addAttribute("hasPrevious", foodPage.hasPrevious());
        model.addAttribute("favoriteRecommendations", favoriteRecommendations);
        model.addAttribute("recentSearches", recentSearches);
        
        return "food/list";
    }
    
    @GetMapping("/new")
    public String createForm(Model model) {
        Food food = new Food();
        model.addAttribute("food", food);
        model.addAttribute("hasImage", false);
        return "food/form";
    }
    
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, 
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Long foodId,
                          Model model) {
        Food food = foodService.getFoodById(id);
        if (food == null) {
            return "redirect:/foods";
        }
        model.addAttribute("food", food);
        model.addAttribute("hasImage", food.getImagePath() != null && fileStorageService.imageExists(food.getImagePath()));
        
        // 將查詢參數傳遞給視圖，以便返回時使用
        model.addAttribute("returnKeyword", keyword);
        model.addAttribute("returnPage", page);
        model.addAttribute("returnFoodId", foodId != null ? foodId : id);
        
        return "food/form";
    }
    
    @PostMapping
    public String save(@ModelAttribute Food food, 
                      @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                      @RequestParam(value = "removeImage", required = false) Boolean removeImage,
                      @RequestParam(value = "returnKeyword", required = false) String returnKeyword,
                      @RequestParam(value = "returnPage", required = false) Integer returnPage,
                      @RequestParam(value = "returnFoodId", required = false) Long returnFoodId,
                      RedirectAttributes redirectAttributes,
                      Model model) {
        
        // 手動驗證必填欄位
        if (food.getName() == null || food.getName().trim().isEmpty()) {
            model.addAttribute("error", "食物名稱為必填欄位");
            model.addAttribute("food", food);
            model.addAttribute("hasImage", false);
            model.addAttribute("returnKeyword", returnKeyword);
            model.addAttribute("returnPage", returnPage);
            model.addAttribute("returnFoodId", returnFoodId);
            return "food/form";
        }
        
        // 驗證至少填寫系數或碳水化合物其中一項
        boolean hasCoefficient = food.getCoefficient() != null && food.getCoefficient() != 0.0;
        boolean hasCarbGrams = food.getCarbGrams() != null && food.getCarbGrams() != 0.0;
        
        if (!hasCoefficient && !hasCarbGrams) {
            model.addAttribute("error", "請至少填寫系數或碳水化合物其中一項");
            model.addAttribute("food", food);
            model.addAttribute("hasImage", false);
            model.addAttribute("returnKeyword", returnKeyword);
            model.addAttribute("returnPage", returnPage);
            model.addAttribute("returnFoodId", returnFoodId);
            return "food/form";
        }
        
        try {
            // 如果是編輯操作，先取得原有資料
            Food existingFood = null;
            if (food.getId() != null) {
                existingFood = foodService.getFoodById(food.getId());
                // 如果沒有移除圖片，保留原有的圖片資訊
                if (!Boolean.TRUE.equals(removeImage) && existingFood != null) {
                    food.setImagePath(existingFood.getImagePath());
                    food.setImageContentType(existingFood.getImageContentType());
                }
            }
            
            // 先儲存食物資料以取得 ID
            Food savedFood = foodService.saveFood(food);
            
            // 處理圖片移除（優先處理）
            if (Boolean.TRUE.equals(removeImage)) {
                // 如果有舊圖片，刪除它
                if (existingFood != null && existingFood.getImagePath() != null) {
                    fileStorageService.deleteImage(existingFood.getImagePath());
                }
                // 清空圖片資訊
                savedFood.setImagePath(null);
                savedFood.setImageContentType(null);
                foodService.saveFood(savedFood);
            }
            // 處理圖片上傳
            else if (imageFile != null && !imageFile.isEmpty()) {
                String contentType = imageFile.getContentType();
                if (contentType != null && (
                    contentType.equals(MediaType.IMAGE_JPEG_VALUE) ||
                    contentType.equals(MediaType.IMAGE_PNG_VALUE) ||
                    contentType.equals(MediaType.IMAGE_GIF_VALUE))) {
                    
                    // 刪除舊圖片
                    if (existingFood != null && existingFood.getImagePath() != null) {
                        fileStorageService.deleteImage(existingFood.getImagePath());
                    }
                    
                    // 儲存新圖片
                    String filename = fileStorageService.storeImage(imageFile, savedFood.getId());
                    savedFood.setImagePath(filename);
                    savedFood.setImageContentType(contentType);
                    foodService.saveFood(savedFood);
                } else {
                    redirectAttributes.addFlashAttribute("error", "只支援 JPEG、PNG 或 GIF 格式的圖片");
                    StringBuilder errorRedirect = new StringBuilder("redirect:/foods/" + savedFood.getId() + "/edit");
                    List<String> errorParams = new ArrayList<>();
                    try {
                        if (returnKeyword != null && !returnKeyword.trim().isEmpty()) {
                            errorParams.add("keyword=" + URLEncoder.encode(returnKeyword, "UTF-8"));
                        }
                        if (returnPage != null) {
                            errorParams.add("page=" + returnPage);
                        }
                        if (returnFoodId != null) {
                            errorParams.add("foodId=" + returnFoodId);
                        }
                        if (!errorParams.isEmpty()) {
                            errorRedirect.append("?").append(String.join("&", errorParams));
                        }
                    } catch (UnsupportedEncodingException e) {
                        // UTF-8 should always be supported
                    }
                    return errorRedirect.toString();
                }
            }
            // 沒有上傳新圖片也沒有移除圖片時，保持原有的圖片資訊
            else if (existingFood != null && existingFood.getImagePath() != null) {
                savedFood.setImagePath(existingFood.getImagePath());
                savedFood.setImageContentType(existingFood.getImageContentType());
                foodService.saveFood(savedFood);
            }
            
            redirectAttributes.addFlashAttribute("success", "食物資料已成功儲存");
            
            // 建立重定向 URL，保留搜尋條件和分頁資訊
            StringBuilder redirectUrl = new StringBuilder("redirect:/foods");
            List<String> params = new ArrayList<>();
            
            try {
                if (returnKeyword != null && !returnKeyword.trim().isEmpty()) {
                    params.add("keyword=" + URLEncoder.encode(returnKeyword, "UTF-8"));
                }
                if (returnPage != null) {
                    params.add("page=" + returnPage);
                }
                
                if (!params.isEmpty()) {
                    redirectUrl.append("?").append(String.join("&", params));
                }
            } catch (UnsupportedEncodingException e) {
                // UTF-8 should always be supported
            }
            
            // 添加錨點
            Long targetFoodId = returnFoodId != null ? returnFoodId : savedFood.getId();
            redirectUrl.append("#food-").append(targetFoodId);
            
            return redirectUrl.toString();
            
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "圖片處理失敗，請稍後再試");
            if (food.getId() != null) {
                StringBuilder errorRedirect = new StringBuilder("redirect:/foods/" + food.getId() + "/edit");
                List<String> errorParams = new ArrayList<>();
                try {
                    if (returnKeyword != null && !returnKeyword.trim().isEmpty()) {
                        errorParams.add("keyword=" + URLEncoder.encode(returnKeyword, "UTF-8"));
                    }
                    if (returnPage != null) {
                        errorParams.add("page=" + returnPage);
                    }
                    if (returnFoodId != null) {
                        errorParams.add("foodId=" + returnFoodId);
                    }
                    if (!errorParams.isEmpty()) {
                        errorRedirect.append("?").append(String.join("&", errorParams));
                    }
                } catch (UnsupportedEncodingException ex) {
                    // UTF-8 should always be supported
                }
                return errorRedirect.toString();
            } else {
                return "redirect:/foods/new";
            }
        }
    }
    
    @DeleteMapping("/{id}")
    @ResponseBody
    public void delete(@PathVariable Long id) {
        Food food = foodService.getFoodById(id);
        if (food != null && food.getImagePath() != null) {
            fileStorageService.deleteImage(food.getImagePath());
        }
        foodService.deleteFood(id);
    }
    
    @PostMapping("/{id}/toggle-favorite")
    @ResponseBody
    public Map<String, Object> toggleFavorite(@PathVariable Long id) {
        Food food = foodService.toggleFavorite(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", food != null);
        response.put("isFavorite", food != null ? food.getIsFavorite() : false);
        return response;
    }
    
    // 搜尋歷史管理方法
    @SuppressWarnings("unchecked")
    private void addToSearchHistory(HttpSession session, String keyword) {
        List<String> searchHistory = (List<String>) session.getAttribute("searchHistory");
        if (searchHistory == null) {
            searchHistory = new ArrayList<>();
        }
        
        // 移除已存在的相同關鍵字（避免重複）
        searchHistory.remove(keyword);
        
        // 添加到開頭
        searchHistory.add(0, keyword);
        
        // 只保留最近50次搜尋
        if (searchHistory.size() > 50) {
            searchHistory = searchHistory.subList(0, 50);
        }
        
        session.setAttribute("searchHistory", searchHistory);
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getRecentSearches(HttpSession session) {
        List<String> searchHistory = (List<String>) session.getAttribute("searchHistory");
        return searchHistory != null ? new ArrayList<>(searchHistory) : new ArrayList<>();
    }
    
    // 獲取完整推薦資料的API
    @GetMapping("/recommendations")
    @ResponseBody
    public Map<String, Object> getFullRecommendations(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        // 獲取所有最愛食物
        List<Food> allFavorites = foodService.getAllFavorites();
        
        // 獲取所有近期搜尋
        List<String> allRecentSearches = getRecentSearches(session);
        
        result.put("favorites", allFavorites);
        result.put("recentSearches", allRecentSearches);
        
        return result;
    }
    
    // 提供影像檔案存取
    @GetMapping("/images/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        try {
            Path filePath = fileStorageService.getImagePath(filename);
            if (filePath == null || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                // 從檔名判斷 content type
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}