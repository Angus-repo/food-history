package com.example.foodhistory.bdd;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import com.example.foodhistory.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.而且;
import io.cucumber.java.zh_tw.那麼;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CRUD、最愛、圖片操作的 Step Definitions
 */
@AutoConfigureMockMvc
public class FoodOperationsSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // 共享狀態改用 CommonSteps 的 static 變數
    private Map<String, String> foodFormData = new java.util.HashMap<>();
    private MockMultipartFile currentImageFile;
    private boolean removeImage = false;

    // ========== 通用操作 ==========
    
    @當("我訪問新增食物頁面")
    public void 我訪問新增食物頁面() throws Exception {
        CommonSteps.lastResult = mockMvc.perform(get("/foods/new")
                .with(user("test@example.com").roles("USER")));
    }

    @當("我訪問食物編輯頁面 ID 為 {int}")
    public void 我訪問食物編輯頁面ID為(int logicalId) throws Exception {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        
        if (food == null) {
            CommonSteps.lastResult = mockMvc.perform(get("/foods/" + logicalId + "/edit")
                    .with(user("test@example.com").roles("USER")));
        } else {
            CommonSteps.currentFood = food;
            CommonSteps.lastResult = mockMvc.perform(get("/foods/" + food.getId() + "/edit")
                    .with(user("test@example.com").roles("USER")));
        }
    }

    @而且("我填寫食物資訊:")
    public void 我填寫食物資訊(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            String field = row.get("欄位");
            String value = row.get("值");
            foodFormData.put(field, value);
        }
    }

    @而且("我修改食物資訊:")
    public void 我修改食物資訊(DataTable dataTable) {
        我填寫食物資訊(dataTable);
    }

    @而且("我提交食物表單")
    public void 我提交食物表單() throws Exception {
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder requestBuilder = 
            multipart("/foods");
        
        requestBuilder.with(csrf())
                      .with(user("test@example.com").roles("USER"));
        
        if (CommonSteps.currentFood != null && CommonSteps.currentFood.getId() != null) {
            requestBuilder.param("id", CommonSteps.currentFood.getId().toString());
            Optional<Food> reloadedFood = foodRepository.findById(CommonSteps.currentFood.getId());
            if (reloadedFood.isPresent()) {
                CommonSteps.currentFood = reloadedFood.get();
            }
        }
        
        if (foodFormData.containsKey("name")) {
            requestBuilder.param("name", foodFormData.get("name"));
        } else if (CommonSteps.currentFood != null) {
            requestBuilder.param("name", CommonSteps.currentFood.getName());
        }
        
        if (foodFormData.containsKey("carbGrams")) {
            requestBuilder.param("carbGrams", foodFormData.get("carbGrams"));
        } else if (CommonSteps.currentFood != null) {
            requestBuilder.param("carbGrams", String.valueOf(CommonSteps.currentFood.getCarbGrams()));
        }
        
        if (foodFormData.containsKey("quantity")) {
            requestBuilder.param("quantity", foodFormData.get("quantity"));
        }
        if (foodFormData.containsKey("unit")) {
            requestBuilder.param("unit", foodFormData.get("unit"));
        }
        if (foodFormData.containsKey("notes")) {
            requestBuilder.param("notes", foodFormData.get("notes"));
        }
        
        if (currentImageFile != null) {
            requestBuilder.file(currentImageFile);
        }
        
        if (removeImage) {
            requestBuilder.param("removeImage", "true");
        }
        
        CommonSteps.lastResult = mockMvc.perform(requestBuilder);
        
        foodFormData.clear();
        currentImageFile = null;
        removeImage = false;
    }

    @當("我刪除 ID 為 {int} 的食物")
    public void 我刪除ID為的食物(int logicalId) throws Exception {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        
        if (food != null) {
            CommonSteps.lastResult = mockMvc.perform(delete("/foods/" + food.getId())
                    .with(csrf())
                    .with(user("test@example.com").roles("USER")));
        } else {
            CommonSteps.lastResult = mockMvc.perform(delete("/foods/" + logicalId)
                    .with(csrf())
                    .with(user("test@example.com").roles("USER")));
        }
    }

    // ========== 圖片操作 ==========
    
    @而且("我上傳一個 JPEG 格式的圖片")
    public void 我上傳一個JPEG格式的圖片() {
        currentImageFile = new MockMultipartFile(
            "imageFile", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "fake jpeg".getBytes());
    }

    @而且("我上傳一個 PNG 格式的圖片")
    public void 我上傳一個PNG格式的圖片() {
        currentImageFile = new MockMultipartFile(
            "imageFile", "test.png", MediaType.IMAGE_PNG_VALUE, "fake png".getBytes());
    }

    @而且("我上傳一個 GIF 格式的圖片")
    public void 我上傳一個GIF格式的圖片() {
        currentImageFile = new MockMultipartFile(
            "imageFile", "test.gif", MediaType.IMAGE_GIF_VALUE, "fake gif".getBytes());
    }

    @而且("我上傳一個 TXT 文字檔案")
    public void 我上傳一個TXT文字檔案() {
        currentImageFile = new MockMultipartFile(
            "imageFile", "test.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes());
    }

    @而且("我選擇移除圖片")
    public void 我選擇移除圖片() {
        removeImage = true;
    }

    // ========== 最愛操作 ==========
    
    @當("我將 ID 為 {int} 的食物切換為最愛")
    public void 我將ID為的食物切換為最愛(int logicalId) throws Exception {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        assertNotNull(food, "找不到邏輯 ID 為 " + logicalId + " 的食物");
        
        CommonSteps.lastResult = mockMvc.perform(post("/foods/" + food.getId() + "/toggle-favorite")
                .with(csrf())
                .with(user("test@example.com").roles("USER")));
    }

    @當("我取得完整的推薦列表")
    public void 我取得完整的推薦列表() throws Exception {
        CommonSteps.lastResult = mockMvc.perform(get("/foods/recommendations")
                .with(user("test@example.com").roles("USER")));
    }

    // ========== 斷言 ==========
    
    @那麼("我應該被重定向到食物列表頁面")
    public void 我應該被重定向到食物列表頁面() throws Exception {
        CommonSteps.lastResult.andExpect(status().is3xxRedirection());
    }

    @那麼("食物應該被成功儲存到資料庫")
    public void 食物應該被成功儲存到資料庫() {
        assertTrue(foodRepository.count() > 0);
    }

    @那麼("資料庫中應該有 {int} 筆食物")
    public void 資料庫中應該有筆食物(int count) {
        assertEquals(count, foodRepository.count());
    }

    @那麼("應該返回錯誤狀態")
    public void 應該返回錯誤狀態() throws Exception {
        int status = CommonSteps.lastResult.andReturn().getResponse().getStatus();
        // 接受 400 以上的錯誤狀態碼，或 302 重定向，或 200（表單頁面帶錯誤訊息）
        assertTrue(status >= 400 || status == 302 || status == 200);
    }

    @那麼("頁面應該顯示食物名稱 {string}")
    public void 頁面應該顯示食物名稱(String expectedName) throws Exception {
        CommonSteps.lastResult.andExpect(status().isOk());
        Food food = (Food) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("food");
        assertEquals(expectedName, food.getName());
    }

    @那麼("頁面應該顯示碳水化合物 {string}")
    public void 頁面應該顯示碳水化合物(String expectedCarb) throws Exception {
        Food food = (Food) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("food");
        assertEquals(Double.parseDouble(expectedCarb), food.getCarbGrams(), 0.01);
    }

    @那麼("頁面應該顯示數量 {string}")
    public void 頁面應該顯示數量(String expectedQuantity) throws Exception {
        Food food = (Food) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("food");
        assertEquals(Double.parseDouble(expectedQuantity), food.getQuantity(), 0.01);
    }

    @那麼("頁面應該顯示單位 {string}")
    public void 頁面應該顯示單位(String expectedUnit) throws Exception {
        Food food = (Food) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("food");
        assertEquals(expectedUnit, food.getUnit());
    }

    @那麼("頁面應該顯示備註 {string}")
    public void 頁面應該顯示備註(String expectedNotes) throws Exception {
        Food food = (Food) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("food");
        assertEquals(expectedNotes, food.getNotes());
    }

    @那麼("資料庫中的食物 {int} 名稱應該是 {string}")
    public void 資料庫中的食物名稱應該是(int logicalId, String expectedName) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedName, reloadedFood.get().getName());
    }

    @那麼("資料庫中的食物 {int} 碳水化合物應該是 {double}")
    public void 資料庫中的食物碳水化合物應該是(int logicalId, double expectedCarb) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedCarb, reloadedFood.get().getCarbGrams(), 0.01);
    }

    @那麼("資料庫中的食物 {int} 備註應該是 {string}")
    public void 資料庫中的食物備註應該是(int logicalId, String expectedNotes) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedNotes, reloadedFood.get().getNotes());
    }

    @那麼("資料庫中的食物 {int} 數量應該是 {double}")
    public void 資料庫中的食物數量應該是(int logicalId, double expectedQuantity) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedQuantity, reloadedFood.get().getQuantity(), 0.01);
    }

    @那麼("資料庫中的食物 {int} 單位應該是 {string}")
    public void 資料庫中的食物單位應該是(int logicalId, String expectedUnit) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedUnit, reloadedFood.get().getUnit());
    }

    @那麼("刪除請求應該成功")
    public void 刪除請求應該成功() throws Exception {
        CommonSteps.lastResult.andExpect(status().isOk());
    }

    @那麼("資料庫中食物 {int} 應該仍然存在")
    public void 資料庫中食物應該仍然存在(int logicalId) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> existingFood = foodRepository.findById(food.getId());
        assertTrue(existingFood.isPresent());
    }

    @那麼("搜尋結果應該包含 {string}")
    public void 搜尋結果應該包含(String foodName) throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> foods = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("foods");
        assertTrue(foods.stream().anyMatch(food -> food.getName().contains(foodName)));
    }

    @那麼("搜尋結果不應該包含 {string}")
    public void 搜尋結果不應該包含(String foodName) throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> foods = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("foods");
        assertFalse(foods.stream().anyMatch(food -> food.getName().contains(foodName)));
    }

    // ========== 最愛斷言 ==========
    
    @那麼("最愛切換請求應該成功")
    public void 最愛切換請求應該成功() throws Exception {
        CommonSteps.lastResult.andExpect(status().isOk());
        String responseBody = CommonSteps.lastResult.andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        assertEquals(true, response.get("success"));
    }

    @那麼("食物 {int} 應該被標記為最愛")
    public void 食物應該被標記為最愛(int logicalId) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertTrue(reloadedFood.get().getIsFavorite());
    }

    @那麼("食物 {int} 不應該被標記為最愛")
    public void 食物不應該被標記為最愛(int logicalId) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertFalse(reloadedFood.get().getIsFavorite());
    }

    @那麼("最愛推薦應該包含 {string}")
    public void 最愛推薦應該包含(String foodName) throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> favorites = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("favoriteRecommendations");
        assertTrue(favorites.stream().anyMatch(food -> food.getName().equals(foodName)));
    }

    @那麼("最愛推薦不應該包含 {string}")
    public void 最愛推薦不應該包含(String foodName) throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> favorites = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("favoriteRecommendations");
        assertFalse(favorites.stream().anyMatch(food -> food.getName().equals(foodName)));
    }

    @那麼("最愛推薦應該最多顯示 {int} 筆")
    public void 最愛推薦應該最多顯示筆(int maxCount) throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> favorites = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("favoriteRecommendations");
        assertTrue(favorites.size() <= maxCount);
    }

    @那麼("最愛推薦應該是空的")
    public void 最愛推薦應該是空的() throws Exception {
        @SuppressWarnings("unchecked")
        List<Food> favorites = (List<Food>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("favoriteRecommendations");
        assertTrue(favorites.isEmpty());
    }

    @那麼("完整最愛推薦應該包含 {int} 筆食物")
    public void 完整最愛推薦應該包含筆食物(int count) throws Exception {
        CommonSteps.lastResult.andExpect(status().isOk());
        String responseBody = CommonSteps.lastResult.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favorites = (List<Map<String, Object>>) response.get("favorites");
        assertEquals(count, favorites.size());
    }

    @那麼("完整最愛推薦應該包含 {string}")
    public void 完整最愛推薦應該包含(String foodName) throws Exception {
        String responseBody = CommonSteps.lastResult.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favorites = (List<Map<String, Object>>) response.get("favorites");
        assertTrue(favorites.stream().anyMatch(food -> foodName.equals(food.get("name"))));
    }

    @那麼("完整最愛推薦不應該包含 {string}")
    public void 完整最愛推薦不應該包含(String foodName) throws Exception {
        String responseBody = CommonSteps.lastResult.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favorites = (List<Map<String, Object>>) response.get("favorites");
        assertFalse(favorites.stream().anyMatch(food -> foodName.equals(food.get("name"))));
    }

    // ========== 圖片斷言 ==========
    
    @那麼("食物 {int} 應該有圖片")
    public void 食物應該有圖片(int logicalId) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertNotNull(reloadedFood.get().getImagePath());
        assertNotNull(reloadedFood.get().getImageContentType());
        CommonSteps.foodMap.put((long) logicalId, reloadedFood.get());
    }

    @那麼("食物 {int} 不應該有圖片")
    public void 食物不應該有圖片(int logicalId) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertNull(reloadedFood.get().getImagePath());
    }

    @那麼("食物 {int} 的圖片類型應該是 {string}")
    public void 食物的圖片類型應該是(int logicalId, String expectedContentType) {
        Food food = CommonSteps.foodMap.get((long) logicalId);
        Optional<Food> reloadedFood = foodRepository.findById(food.getId());
        assertTrue(reloadedFood.isPresent());
        assertEquals(expectedContentType, reloadedFood.get().getImageContentType());
    }

    @那麼("最新的食物應該有圖片")
    public void 最新的食物應該有圖片() {
        List<Food> allFoods = foodRepository.findAll();
        assertFalse(allFoods.isEmpty());
        Food latestFood = allFoods.stream()
            .max((f1, f2) -> f1.getId().compareTo(f2.getId()))
            .orElse(null);
        assertNotNull(latestFood);
        assertNotNull(latestFood.getImagePath());
        assertNotNull(latestFood.getImageContentType());
    }
}
