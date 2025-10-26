package com.example.foodhistory.bddui;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.repository.FoodRepository;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, MockOAuth2LoginConfig.class})
public class UISearchSteps {

    @Autowired
    private FoodRepository foodRepository;

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
        
        // 從 DataTable 讀取並創建食物資料
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Food food = new Food();
            food.setId(Long.parseLong(row.get("id")));
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            foodRepository.save(food);
        }
    }

    @假如("資料庫中有一個沒有圖片的食物:")
    public void 資料庫中有一個沒有圖片的食物(DataTable dataTable) {
        // 清空現有資料
        foodRepository.deleteAll();
        
        // 從 DataTable 讀取並創建食物資料
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            Food food = new Food();
            food.setId(Long.parseLong(row.get("id")));
            food.setName(row.get("name"));
            food.setCarbGrams(Double.parseDouble(row.get("carbGrams")));
            // imagePath 為空或 null
            String imagePath = row.get("imagePath");
            if (imagePath != null && !imagePath.trim().isEmpty()) {
                food.setImagePath(imagePath);
            }
            foodRepository.save(food);
        }
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
        // 尋找包含該食物名稱的食物卡片
        WebElement foodCard = wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[contains(@class, 'food-card')]//h3[contains(., '" + foodName + "')]/ancestor::div[contains(@class, 'food-card')]")
            )
        );
        
        // 檢查卡片中是否有預設圖片容器（不含 img 標籤，而是顯示佔位符的 div）
        List<WebElement> placeholderDivs = foodCard.findElements(
            By.xpath(".//div[contains(@class, 'food-card-image') and not(self::img)]")
        );
        
        assertFalse(placeholderDivs.isEmpty(), 
            "食物 " + foodName + " 應該顯示預設圖片容器");
    }

    @那麼("預設圖片應該包含圖示")
    public void 預設圖片應該包含圖示() {
        // 查找食物卡片中預設圖片的圖示
        // list.html中預設圖片顯示為: <div class="food-card-image ..."><i class="bi bi-image"></i></div>
        try {
            WebElement icon = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".food-card .food-card-image i.bi-image, .food-card div[class*='food-card-image'] i.bi-image")
                )
            );
            assertTrue(icon.isDisplayed(), "預設圖片應該包含 bi-image 圖示");
        } catch (Exception e) {
            // 如果找不到,列印頁面結構以便調試
            System.out.println("無法找到預設圖片圖示,頁面HTML:");
            try {
                List<WebElement> cards = driver.findElements(By.className("food-card"));
                for (int i = 0; i < Math.min(cards.size(), 2); i++) {
                    System.out.println("卡片 " + i + ": " + cards.get(i).getAttribute("outerHTML").substring(0, Math.min(500, cards.get(i).getAttribute("outerHTML").length())));
                }
            } catch (Exception ex) {
                // 忽略
            }
            fail("預設圖片應該包含 bi-image 圖示");
        }
    }
}
