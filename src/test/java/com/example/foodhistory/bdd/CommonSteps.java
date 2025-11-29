package com.example.foodhistory.bdd;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import com.example.foodhistory.repository.UserRepository;
import com.example.foodhistory.model.User;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假如;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 共享的 Step Definitions，避免重複定義
 */
@AutoConfigureMockMvc
public class CommonSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    
    // 共享的測試狀態
    public static Map<Long, Food> foodMap = new java.util.HashMap<>(); // 邏輯 ID 到實際 Food 的映射
    public static ResultActions lastResult; // 上一個請求的結果
    public static Food currentFood; // 當前操作的食物

    @Before
    public void setup() {
        // 清理測試數據
        foodRepository.deleteAll();
        userRepository.deleteAll();
        foodMap.clear();
        
        // 創建測試用戶
        if (!userRepository.findByEmail("test@example.com").isPresent()) {
            testUser = new User();
            testUser.setEmail("test@example.com");
            testUser.setUsername("testuser");
            testUser.setEncryptedPassword("$2a$10$dummypassword");
            testUser.setRole("USER");
            testUser.setEnabled(true);
            testUser = userRepository.save(testUser);
        } else {
            testUser = userRepository.findByEmail("test@example.com").get();
        }
    }

    @假如("我已登入系統")
    public void 我已登入系統() {
        // 在請求中使用 .with(user(...)) 來模擬登入
    }

    @假如("資料庫中有以下食物:")
    public void 資料庫中有以下食物(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Long logicalId = Long.parseLong(row.get("id"));
            
            Food food = new Food();
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            
            if (row.containsKey("quantity") && row.get("quantity") != null && !row.get("quantity").isEmpty()) {
                food.setQuantity(Double.parseDouble(row.get("quantity")));
            }
            if (row.containsKey("unit")) {
                food.setUnit(row.get("unit"));
            }
            if (row.containsKey("notes")) {
                food.setNotes(row.get("notes"));
            }
            if (row.containsKey("coefficient") && row.get("coefficient") != null && !row.get("coefficient").isEmpty()) {
                food.setCoefficient(Double.parseDouble(row.get("coefficient")));
            }
            if (row.containsKey("isFavorite")) {
                food.setIsFavorite(Boolean.parseBoolean(row.get("isFavorite")));
            } else {
                food.setIsFavorite(false);
            }
            if (row.containsKey("imagePath") && row.get("imagePath") != null && !row.get("imagePath").isEmpty()) {
                food.setImagePath(row.get("imagePath"));
            }
            
            food = foodRepository.save(food);
            foodMap.put(logicalId, food);
        }
    }
}
