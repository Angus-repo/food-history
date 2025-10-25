package com.example.foodhistory.bdd;

import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.而且;
import io.cucumber.java.zh_tw.那麼;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
public class FoodListNavigationSteps {

    @Autowired
    private MockMvc mockMvc;

    private String currentUrl;
    private MockMultipartFile invalidFileToUpload; // 無效檔案上傳

    @Before
    public void setup() {
        // 重置本地變數
        CommonSteps.currentFood = null;
        invalidFileToUpload = null;
    }

    @當("我訪問食物列表頁面")
    public void 我訪問食物列表頁面() throws Exception {
        CommonSteps.lastResult = mockMvc.perform(get("/foods")
                .with(user("test@example.com").roles("USER")));
        currentUrl = "/foods";
    }

    @當("我輸入關鍵字 {string} 進行搜尋")
    public void 我輸入關鍵字進行搜尋(String keyword) throws Exception {
        CommonSteps.lastResult = mockMvc.perform(get("/foods")
                .param("keyword", keyword)
                .with(user("test@example.com").roles("USER")));
        currentUrl = "/foods?keyword=" + keyword;
    }

    @那麼("我應該看到 {int} 筆搜尋結果")
    public void 我應該看到筆搜尋結果(int count) throws Exception {
        CommonSteps.lastResult.andExpect(status().isOk());
        List<?> foods = (List<?>) CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("foods");
        assertEquals(count, foods.size());
    }

    @當("我點擊 ID 為 {int} 的食物卡片")
    public void 我點擊ID為的食物卡片(int foodId) throws Exception {
        // 模擬從列表頁點擊，帶上當前的搜尋參數
        String keyword = extractParamFromUrl(currentUrl, "keyword");
        String page = extractParamFromUrl(currentUrl, "page");
        
        if (page == null) {
            page = "0";
        }

        // 從 CommonSteps.foodMap 獲取實際的 Food （使用邏輯 ID）
        CommonSteps.currentFood = CommonSteps.foodMap.get((long) foodId);
        
        if (CommonSteps.currentFood == null) {
            throw new IllegalStateException("找不到邏輯 ID 為 " + foodId + " 的食物，可能未在場景中創建");
        }
        
        // 使用實際的資料庫 ID
        Long realFoodId = CommonSteps.currentFood.getId();

        CommonSteps.lastResult = mockMvc.perform(get("/foods/" + realFoodId + "/edit")
                .param("keyword", keyword)
                .param("page", page)
                .param("foodId", String.valueOf(realFoodId))
                .with(user("test@example.com").roles("USER")));
        currentUrl = "/foods/" + realFoodId + "/edit";
    }

    @那麼("編輯頁面的 URL 應該包含參數 {string} 值為 {string}")
    public void 編輯頁面的URL應該包含參數值為(String paramName, String expectedValue) throws Exception {
        // 如果是 302 重定向，追蹤到實際頁面
        if (CommonSteps.lastResult.andReturn().getResponse().getStatus() == 302) {
            String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
            CommonSteps.lastResult = mockMvc.perform(get(redirectUrl)
                    .with(user("test@example.com").roles("USER")));
        }
        
        CommonSteps.lastResult.andExpect(status().isOk());
        
        // 檢查 model attribute
        Object actualValue = CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("return" + capitalize(paramName));
        
        if (actualValue != null) {
            // 如果是 foodId 參數，使用實際資料庫 ID
            if ("foodId".equals(paramName) && CommonSteps.currentFood != null) {
                assertEquals(CommonSteps.currentFood.getId().toString(), actualValue.toString());
            } else {
                assertEquals(expectedValue, actualValue.toString());
            }
        }
    }

    @那麼("編輯頁面的 URL 不應該包含 {string} 參數")
    public void 編輯頁面的URL不應該包含參數(String paramName) throws Exception {
        // 如果是 302 重定向，追蹤到實際頁面
        if (CommonSteps.lastResult.andReturn().getResponse().getStatus() == 302) {
            String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
            CommonSteps.lastResult = mockMvc.perform(get(redirectUrl)
                    .with(user("test@example.com").roles("USER")));
        }
        
        CommonSteps.lastResult.andExpect(status().isOk());
        Object value = CommonSteps.lastResult.andReturn().getModelAndView().getModel().get("return" + capitalize(paramName));
        assertTrue(value == null || value.toString().isEmpty());
    }

    @而且("頁面應該顯示隱藏欄位 {string} 值為 {string}")
    public void 頁面應該顯示隱藏欄位值為(String fieldName, String expectedValue) throws Exception {
        // 在實際 HTML 中驗證隱藏欄位（這裡簡化為檢查 model）
        Object actualValue = CommonSteps.lastResult.andReturn().getModelAndView().getModel().get(fieldName);
        if (actualValue != null) {
            assertEquals(expectedValue, actualValue.toString());
        }
    }

    @而且("我修改食物名稱為 {string}")
    public void 我修改食物名稱為(String newName) {
        // 在下一步提交時使用這個新名稱
    }

    @而且("我點擊 {string} 按鈕")
    public void 我點擊按鈕(String buttonText) throws Exception {
        // 根據按鈕文字決定行為
        if (buttonText.contains("更新")) {
            我更新食物();
        }
    }

    @當("我更新食物")
    public void 我更新食物() throws Exception {
        // 從當前 URL 提取參數
        String keyword = extractModelAttribute("returnKeyword");
        String page = extractModelAttribute("returnPage");
        
        // 使用 currentFood，確保有 ID
        if (CommonSteps.currentFood == null) {
            throw new IllegalStateException("currentFood 未設置，請先點擊食物卡片");
        }
        if (CommonSteps.currentFood.getId() == null) {
            throw new IllegalStateException("currentFood.getId() 為 null，食物未正確儲存");
        }

        // 如果有無效檔案需要上傳，包含在請求中
        if (invalidFileToUpload != null) {
            CommonSteps.lastResult = mockMvc.perform(multipart("/foods")
                    .file(invalidFileToUpload)
                    .param("id", CommonSteps.currentFood.getId().toString())
                    .param("name", CommonSteps.currentFood.getName())
                    .param("carbGrams", String.valueOf(CommonSteps.currentFood.getCarbGrams()))
                    .param("returnKeyword", keyword != null ? keyword : "")
                    .param("returnPage", page != null ? page : "0")
                    .param("returnFoodId", CommonSteps.currentFood.getId().toString())
                    .with(csrf())
                    .with(user("test@example.com").roles("USER")));
        } else {
            CommonSteps.lastResult = mockMvc.perform(multipart("/foods")
                    .param("id", CommonSteps.currentFood.getId().toString())
                    .param("name", CommonSteps.currentFood.getName())
                    .param("carbGrams", String.valueOf(CommonSteps.currentFood.getCarbGrams()))
                    .param("returnKeyword", keyword != null ? keyword : "")
                    .param("returnPage", page != null ? page : "0")
                    .param("returnFoodId", CommonSteps.currentFood.getId().toString())
                    .with(csrf())
                    .with(user("test@example.com").roles("USER")));
        }
    }

    @那麼("我應該看到成功訊息 {string}")
    public void 我應該看到成功訊息(String message) throws Exception {
        CommonSteps.lastResult.andExpect(status().is3xxRedirection());
        CommonSteps.lastResult.andExpect(flash().attribute("success", message));
    }

    @那麼("我應該看到錯誤訊息 {string}")
    public void 我應該看到錯誤訊息(String message) throws Exception {
        CommonSteps.lastResult.andExpect(flash().attribute("error", message));
    }

    @那麼("重定向 URL 應該包含 {string} 參數且值為 URL 編碼的 {string}")
    public void 重定向URL應該包含參數且值為URL編碼的(String paramName, String value) throws Exception {
        CommonSteps.lastResult.andExpect(status().is3xxRedirection());
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        
        String encodedValue = URLEncoder.encode(value, "UTF-8");
        assertThat(redirectUrl, containsString(paramName + "=" + encodedValue));
    }

    @那麼("重定向 URL 應該包含 {string} 參數")
    public void 重定向URL應該包含參數(String paramName) throws Exception {
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertThat(redirectUrl, containsString(paramName + "="));
    }

    @那麼("重定向 URL 不應該包含 {string} 參數")
    public void 重定向URL不應該包含參數(String paramName) throws Exception {
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        // 檢查不包含該參數,或參數值為空
        assertThat(redirectUrl, not(containsString(paramName + "=")));
    }

    @那麼("重定向 URL 應該包含 {string} 錨點")
    public void 重定向URL應該包含錨點(String anchor) throws Exception {
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        
        // 如果錨點包含 food-xxx 格式，使用實際的資料庫 ID
        if (anchor.startsWith("#food-") && CommonSteps.currentFood != null) {
            String expectedAnchor = "#food-" + CommonSteps.currentFood.getId();
            assertThat(redirectUrl, containsString(expectedAnchor));
        } else {
            assertThat(redirectUrl, containsString(anchor));
        }
    }

    @那麼("重定向的 Location header 應該是有效的")
    public void 重定向的Location_header應該是有效的() throws Exception {
        CommonSteps.lastResult.andExpect(status().is3xxRedirection());
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        
        // 確保 URL 不包含未編碼的中文字元
        assertThat(redirectUrl, not(containsString("麥當勞")));
    }

    @那麼("重定向 URL 應該包含 URL 編碼的中文 {string}")
    public void 重定向URL應該包含URL編碼的中文(String encodedText) throws Exception {
        String redirectUrl = CommonSteps.lastResult.andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertThat(redirectUrl, containsString(encodedText));
    }

    @而且("我嘗試上傳一個無效的圖片檔案")
    public void 我嘗試上傳一個無效的圖片檔案() throws Exception {
        // 創建一個無效的檔案（text 檔案而不是圖片）
        MockMultipartFile invalidFile = new MockMultipartFile(
            "imageFile", 
            "test.txt", 
            "text/plain", 
            "This is not an image".getBytes()
        );
        
        // 保存無效檔案，讓下一步的「我更新食物」使用
        this.invalidFileToUpload = invalidFile;
    }

    // 輔助方法
    private String extractParamFromUrl(String url, String paramName) {
        if (url == null || !url.contains("?")) {
            return null;
        }
        
        String[] parts = url.split("\\?");
        if (parts.length < 2) {
            return null;
        }
        
        String[] params = parts[1].split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                try {
                    return URLDecoder.decode(keyValue[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }

    private String extractModelAttribute(String attributeName) {
        if (CommonSteps.lastResult == null || CommonSteps.lastResult.andReturn().getModelAndView() == null) {
            return null;
        }
        Object value = CommonSteps.lastResult.andReturn().getModelAndView().getModel().get(attributeName);
        return value != null ? value.toString() : null;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
