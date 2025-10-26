package com.example.foodhistory.bddui;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
import com.example.foodhistory.service.FileStorageService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假如;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.foodhistory.config.TestSecurityConfig;
import com.example.foodhistory.config.MockOAuth2LoginConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, MockOAuth2LoginConfig.class})
public class UISearchSteps {

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;

    /**
     * 從共享 WebDriver 管理器獲取實例
     * order = 1 確保在 SharedWebDriverManager 之後執行
     */
    @Before(value = "@ui", order = 1)
    public void setUp() {
        this.driver = SharedWebDriverManager.getDriver();
        this.wait = SharedWebDriverManager.getWait();
        this.baseUrl = SharedWebDriverManager.getBaseUrl();
    }

    @假如("我已登入系統")
    public void 我已登入系統() {
        // Mock OAuth2 認證已經在 SharedWebDriverManager 中設置
        // 這裡只需要訪問首頁來觸發 session 初始化
        driver.get(baseUrl + "/foods");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        System.out.println("✅ 已使用 Mock OAuth2 認證訪問系統");
    }

    @當("資料庫中有以下食物:")
    public void 資料庫中有以下食物(DataTable dataTable) {
        // 清空現有資料
        foodRepository.deleteAll();
        foodRepository.flush();
        
        // 從 DataTable 讀取並創建食物資料
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Food food = new Food();
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            
            // 儲存並取得自動生成的ID
            Food savedFood = foodRepository.saveAndFlush(food);
            
            // 將期望的ID與實際ID的映射儲存起來
            String expectedIdStr = row.get("id");
            if (expectedIdStr != null && !expectedIdStr.isEmpty()) {
                long expectedId = Long.parseLong(expectedIdStr);
                SharedWebDriverManager.mapFoodId(expectedId, savedFood.getId());
                System.out.println("映射 ID: 期望=" + expectedId + " -> 實際=" + savedFood.getId() + ", Name=" + savedFood.getName());
            } else {
                System.out.println("創建食物: ID=" + savedFood.getId() + ", Name=" + savedFood.getName());
            }
        }
        
        // 重新載入所有食物並顯示
        List<Food> allFoods = foodRepository.findAll();
        System.out.println("資料庫中總共有 " + allFoods.size() + " 個食物");
    }

    @假如("資料庫中有一個沒有圖片的食物:")
    public void 資料庫中有一個沒有圖片的食物(DataTable dataTable) {
        // 清空現有資料
        foodRepository.deleteAll();
        foodRepository.flush();
        
        // 從 DataTable 讀取並創建食物資料
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Food food = new Food();
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            // imagePath 為空或 null
            String imagePath = row.get("imagePath");
            if (imagePath != null && !imagePath.trim().isEmpty()) {
                food.setImagePath(imagePath);
            }
            Food savedFood = foodRepository.saveAndFlush(food);
            System.out.println("創建無圖片食物: ID=" + savedFood.getId() + ", Name=" + savedFood.getName());
        }
    }

    @假如("資料庫中有一個帶圖片的食物:")
    public void 資料庫中有一個帶圖片的食物(DataTable dataTable) {
        // 清空現有資料
        foodRepository.deleteAll();
        foodRepository.flush();
        
        // 從 DataTable 讀取並創建食物資料
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Food food = new Food();
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            
            // 設置圖片路徑
            String imagePath = row.get("imagePath");
            food.setImagePath(imagePath);
            food.setImageContentType("image/jpeg");
            
            // 創建實際的測試圖片檔案
            try {
                Path imageFilePath = fileStorageService.getImagePath(imagePath);
                if (imageFilePath != null) {
                    // 確保目錄存在
                    Files.createDirectories(imageFilePath.getParent());
                    
                    // 創建一個最小的 JPEG 圖片
                    byte[] imageData = createMinimalJpegBytes();
                    Files.write(imageFilePath, imageData);
                    System.out.println("✅ 創建測試圖片檔案: " + imageFilePath);
                }
            } catch (IOException e) {
                System.err.println("⚠️  無法創建測試圖片檔案: " + e.getMessage());
                e.printStackTrace();
            }
            
            Food savedFood = foodRepository.saveAndFlush(food);
            System.out.println("創建帶圖片的食物: ID=" + savedFood.getId() + ", Name=" + savedFood.getName() + ", ImagePath=" + savedFood.getImagePath());
        }
    }
    
    /**
     * 創建一個最小的 JPEG 圖片數據（1x1 像素紅色）
     */
    private byte[] createMinimalJpegBytes() {
        return new byte[] {
            (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46, 
            0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48, 
            0x00, 0x48, 0x00, 0x00, (byte)0xFF, (byte)0xDB, 0x00, 0x43, 
            0x00, 0x03, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03, 
            0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x04, 
            0x06, 0x04, 0x04, 0x04, 0x04, 0x04, 0x08, 0x06, 
            0x06, 0x05, 0x06, 0x09, 0x08, 0x0A, 0x0A, 0x09, 
            0x08, 0x09, 0x09, 0x0A, 0x0C, 0x0F, 0x0C, 0x0A, 
            0x0B, 0x0E, 0x0B, 0x09, 0x09, 0x0D, 0x11, 0x0D, 
            0x0E, 0x0F, 0x10, 0x10, 0x11, 0x10, 0x0A, 0x0C, 
            0x12, 0x13, 0x12, 0x10, 0x13, 0x0F, 0x10, 0x10, 
            0x10, (byte)0xFF, (byte)0xC9, 0x00, 0x0B, 0x08, 0x00, 0x01, 
            0x00, 0x01, 0x01, 0x01, 0x11, 0x00, (byte)0xFF, (byte)0xCC, 
            0x00, 0x06, 0x00, 0x10, 0x10, 0x05, (byte)0xFF, (byte)0xDA, 
            0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 
            (byte)0xD2, (byte)0xCF, 0x20, (byte)0xFF, (byte)0xD9
        };
    }

    @當("我訪問食物列表頁面")
    public void 我訪問食物列表頁面() {
        driver.get(baseUrl + "/foods");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
    }

    @當("我訪問新增食物頁面")
    public void 我訪問新增食物頁面() {
        driver.get(baseUrl + "/foods/new");
        // 先等待 body 載入，避免等待不存在的 form 導致超時
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        
        // 檢查是否成功導航到新增頁面（URL 應該包含 /foods/new）
        wait.until(ExpectedConditions.urlContains("/foods/new"));
        
        // 額外等待確保頁面內容載入完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我訪問食物編輯頁面 ID 為 {int}")
    public void 我訪問食物編輯頁面ID為(int foodId) {
        // 先刷新食物列表以確保資料同步
        List<Food> allFoods = foodRepository.findAll();
        System.out.println("資料庫中的所有食物: " + allFoods.size() + " 個");
        for (Food f : allFoods) {
            System.out.println("  - ID: " + f.getId() + ", Name: " + f.getName());
        }
        
        // 從資料庫查找實際的食物 ID
        Food food = allFoods.stream()
            .filter(f -> f.getId().equals((long) foodId))
            .findFirst()
            .orElse(null);
        
        if (food != null) {
            System.out.println("找到食物 ID: " + food.getId() + ", 訪問編輯頁面");
            driver.get(baseUrl + "/foods/" + food.getId() + "/edit");
        } else {
            System.out.println("未找到食物 ID: " + foodId + ", 嘗試直接訪問");
            driver.get(baseUrl + "/foods/" + foodId + "/edit");
        }
        
        // 等待頁面載入
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        
        // 檢查是否成功進入編輯頁面（可能會重定向到列表頁面如果 ID 不存在）
        String currentUrl = driver.getCurrentUrl();
        System.out.println("當前 URL: " + currentUrl);
        
        if (currentUrl.contains("/edit")) {
            System.out.println("成功進入編輯頁面");
        } else if (currentUrl.contains("/foods")) {
            System.out.println("警告: 重定向到列表頁面，可能食物不存在");
            // 重新嘗試查找食物
            allFoods = foodRepository.findAll();
            food = allFoods.stream()
                .filter(f -> f.getId().equals((long) foodId))
                .findFirst()
                .orElse(null);
            
            if (food != null) {
                System.out.println("重新嘗試訪問食物 ID: " + food.getId());
                driver.get(baseUrl + "/foods/" + food.getId() + "/edit");
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                wait.until(ExpectedConditions.urlContains("/edit"));
            }
        }
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我訪問食物列表頁面並搜尋 {string}")
    public void 我訪問食物列表頁面並搜尋(String keyword) {
        driver.get(baseUrl + "/foods?keyword=" + keyword);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
    }

    @當("我在搜尋框輸入 {string}")
    public void 我在搜尋框輸入(String keyword) {
        WebElement searchInput = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.name("keyword"))
        );
        searchInput.clear();
        searchInput.sendKeys(keyword);
    }

    @當("我點擊搜尋按鈕")
    public void 我點擊搜尋按鈕() {
        WebElement searchButton = driver.findElement(
            By.xpath("//button[@type='submit' and contains(., '搜尋')]")
        );
        searchButton.click();
        wait.until(ExpectedConditions.urlContains("keyword="));
    }

    @當("我點擊清除按鈕")
    public void 我點擊清除按鈕() {
        WebElement clearButton = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//a[contains(@class, 'btn') and contains(., '清除')]")
            )
        );
        clearButton.click();
        wait.until(ExpectedConditions.urlMatches(".*\\/foods$"));
    }

    @當("我點擊搜尋框")
    public void 我點擊搜尋框() {
        WebElement searchInput = driver.findElement(By.name("keyword"));
        searchInput.click();
    }

    @當("我按下 {string} 快捷鍵")
    public void 我按下快捷鍵(String shortcut) {
        WebElement body = driver.findElement(By.tagName("body"));
        
        if (shortcut.equals("Ctrl+K")) {
            body.sendKeys(Keys.chord(Keys.CONTROL, "k"));
        } else if (shortcut.equals("Ctrl+N")) {
            body.sendKeys(Keys.chord(Keys.CONTROL, "n"));
        } else if (shortcut.equals("ESC")) {
            body.sendKeys(Keys.ESCAPE);
        }
        
        try {
            Thread.sleep(300); // 等待快捷鍵效果
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @那麼("頁面 URL 應該包含 {string}")
    public void 頁面URL應該包含(String urlPart) {
        // URL 中的中文會被編碼,需要解碼後比較或直接檢查編碼後的 URL
        wait.until(driver -> {
            String currentUrl = driver.getCurrentUrl();
            try {
                String decodedUrl = java.net.URLDecoder.decode(currentUrl, "UTF-8");
                return decodedUrl.contains(urlPart);
            } catch (Exception e) {
                return currentUrl.contains(urlPart);
            }
        });
        String currentUrl = driver.getCurrentUrl();
        try {
            String decodedUrl = java.net.URLDecoder.decode(currentUrl, "UTF-8");
            assertTrue(decodedUrl.contains(urlPart), "URL 應該包含: " + urlPart + ", 實際 URL: " + decodedUrl);
        } catch (Exception e) {
            assertTrue(currentUrl.contains(urlPart), "URL 應該包含: " + urlPart);
        }
    }

    @那麼("頁面 URL 不應該包含 {string}")
    public void 頁面URL不應該包含(String urlPart) {
        assertFalse(driver.getCurrentUrl().contains(urlPart));
    }

    @那麼("我應該看到食物 {string} 在列表中")
    public void 我應該看到食物在列表中(String foodName) {
        WebElement foodElement = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), '" + foodName + "')]")
            )
        );
        assertTrue(foodElement.isDisplayed());
    }

    @那麼("搜尋框應該是空的")
    public void 搜尋框應該是空的() {
        WebElement searchInput = driver.findElement(By.name("keyword"));
        String value = searchInput.getAttribute("value");
        assertTrue(value == null || value.isEmpty());
    }

    @當("我點擊「我的食物歷史」標題連結")
    public void 我點擊我的食物歷史標題連結() {
        WebElement titleLink = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//h1[contains(@class, 'page-title')]//a[@href='/foods']")
            )
        );
        titleLink.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
    }

    @那麼("頁面 URL 應該是 {string}")
    public void 頁面URL應該是(String expectedPath) {
        wait.until(driver -> {
            String currentUrl = driver.getCurrentUrl();
            // 移除 baseUrl 部分，只比較路徑
            String path = currentUrl.replace(baseUrl, "");
            // 移除可能的查詢參數
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"));
            }
            return path.equals(expectedPath);
        });
        
        String currentUrl = driver.getCurrentUrl();
        String path = currentUrl.replace(baseUrl, "");
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        assertEquals(expectedPath, path, "URL 路徑應該是: " + expectedPath);
    }

    @那麼("頁面 URL 不應該包含任何查詢參數")
    public void 頁面URL不應該包含任何查詢參數() {
        String currentUrl = driver.getCurrentUrl();
        assertFalse(currentUrl.contains("?"), "URL 不應該包含查詢參數，當前 URL: " + currentUrl);
    }

    @那麼("應該顯示搜尋建議區域")
    public void 應該顯示搜尋建議區域() {
        try {
            WebElement suggestions = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.className("search-recommendations")
                )
            );
            assertTrue(suggestions.isDisplayed());
        } catch (TimeoutException e) {
            // 搜尋建議區域可能預設就顯示，檢查是否存在
            List<WebElement> elements = driver.findElements(By.className("search-recommendations"));
            assertFalse(elements.isEmpty(), "搜尋建議區域應該存在");
        }
    }

    @那麼("搜尋框應該獲得焦點")
    public void 搜尋框應該獲得焦點() {
        WebElement searchInput = driver.findElement(By.name("keyword"));
        WebElement activeElement = driver.switchTo().activeElement();
        // 比較元素的 name 屬性而不是直接比較對象
        String expectedName = searchInput.getAttribute("name");
        String actualName = activeElement.getAttribute("name");
        assertEquals(expectedName, actualName, "搜尋框應該是當前焦點元素");
    }

    @那麼("應該跳轉到新增食物頁面")
    public void 應該跳轉到新增食物頁面() {
        wait.until(ExpectedConditions.urlContains("/foods/new"));
        assertTrue(driver.getCurrentUrl().contains("/foods/new"));
    }

    @那麼("食物 {string} 應該仍然存在於列表中")
    public void 食物應該仍然存在於列表中(String foodName) {
        WebElement foodElement = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), '" + foodName + "')]")
            )
        );
        assertTrue(foodElement.isDisplayed());
    }

    @那麼("食物 {string} 應該顯示預設圖片")
    public void 食物應該顯示預設圖片(String foodName) {
        // 先等待頁面完全載入
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 尋找包含該食物名稱的食物卡片
        WebElement foodCard = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[contains(@class, 'food-card')]//h3[contains(., '" + foodName + "')]/ancestor::div[contains(@class, 'food-card')]")
            )
        );
        
        System.out.println("找到食物卡片: " + foodName);
        
        // 檢查卡片中是否沒有實際的 img 標籤（應該使用預設圖片）
        List<WebElement> actualImages = foodCard.findElements(
            By.xpath(".//img[contains(@class, 'food-card-image')]")
        );
        
        System.out.println("實際圖片數量: " + actualImages.size());
        if (!actualImages.isEmpty()) {
            for (WebElement img : actualImages) {
                System.out.println("  - 圖片 src: " + img.getAttribute("src"));
            }
        }
        
        // 檢查資料庫中的圖片路徑
        List<Food> foods = foodRepository.findAll();
        for (Food f : foods) {
            if (f.getName().equals(foodName)) {
                System.out.println("資料庫中的圖片路徑: " + f.getImagePath());
            }
        }
        
        // 如果有實際圖片，則測試應該失敗
        assertTrue(actualImages.isEmpty(), 
            "食物 " + foodName + " 不應該有實際圖片，應該顯示預設圖示");
        
        // 檢查是否有預設圖片容器（div 元素且包含 bi-image 圖示）
        List<WebElement> placeholderDivs = foodCard.findElements(
            By.xpath(".//div[contains(@class, 'food-card-image')]//i[contains(@class, 'bi-image')]")
        );
        
        assertFalse(placeholderDivs.isEmpty(), 
            "食物 " + foodName + " 應該顯示預設圖片容器with圖示");
    }

    @那麼("預設圖片應該包含圖示")
    public void 預設圖片應該包含圖示() {
        // 查找食物卡片中預設圖片的圖示
        // list.html中預設圖片顯示為: <div class="food-card-image ..."><i class="bi bi-image"></i></div>
        try {
            // 先等待頁面完全載入
            Thread.sleep(1000);
            
            // 檢查是否有實際的 img 標籤（不應該有）
            List<WebElement> images = driver.findElements(
                By.cssSelector(".food-card img.food-card-image")
            );
            
            if (!images.isEmpty()) {
                System.out.println("警告: 找到 " + images.size() + " 個圖片元素，但預期應該是預設圖示");
                for (WebElement img : images) {
                    System.out.println("  - 圖片 src: " + img.getAttribute("src"));
                }
            }
            
            // 查找預設圖示
            WebElement icon = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".food-card .food-card-image i.bi-image, .food-card div[class*='food-card-image'] i.bi-image")
                )
            );
            assertTrue(icon.isDisplayed(), "預設圖片應該包含 bi-image 圖示");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("等待被中斷");
        } catch (Exception e) {
            // 如果找不到,列印頁面結構以便調試
            System.out.println("無法找到預設圖片圖示,頁面HTML:");
            try {
                List<WebElement> cards = driver.findElements(By.className("food-card"));
                System.out.println("找到 " + cards.size() + " 個食物卡片");
                for (int i = 0; i < Math.min(cards.size(), 2); i++) {
                    String html = cards.get(i).getAttribute("outerHTML");
                    System.out.println("卡片 " + i + " (前500字元): " + html.substring(0, Math.min(500, html.length())));
                    
                    // 也檢查圖片相關的元素
                    List<WebElement> imgElements = cards.get(i).findElements(By.tagName("img"));
                    List<WebElement> iconElements = cards.get(i).findElements(By.cssSelector("i.bi-image"));
                    System.out.println("  - 包含 " + imgElements.size() + " 個 img 標籤");
                    System.out.println("  - 包含 " + iconElements.size() + " 個 bi-image 圖示");
                }
            } catch (Exception ex) {
                System.out.println("調試時發生錯誤: " + ex.getMessage());
            }
            fail("預設圖片應該包含 bi-image 圖示");
        }
    }
}
