package com.example.foodhistory.bddui;

import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.foodhistory.config.TestSecurityConfig;
import com.example.foodhistory.config.MockOAuth2LoginConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, MockOAuth2LoginConfig.class})
public class UIScrollNavigationSteps {

    private WebDriver driver;
    private WebDriverWait wait;

    @Before(value = "@ui", order = 1)
    public void setUp() {
        this.driver = SharedWebDriverManager.getDriver();
        this.wait = SharedWebDriverManager.getWait();
    }

    @當("我點擊 ID 為 {int} 的食物卡片")
    public void 我點擊ID為的食物卡片(int foodId) {
        try {
            // 給頁面更多時間載入
            Thread.sleep(1500);
            
            // 獲取實際的食物ID
            long actualFoodId = SharedWebDriverManager.getActualFoodId(foodId);
            System.out.println("查找食物: 期望ID=" + foodId + ", 實際ID=" + actualFoodId);
            
            // 首先檢查頁面上有多少食物卡片
            java.util.List<WebElement> allFoodCards = driver.findElements(By.cssSelector("[id^='food-']"));
            System.out.println("找到 " + allFoodCards.size() + " 個食物卡片");
            for (WebElement card : allFoodCards) {
                System.out.println("食物卡片 ID: " + card.getAttribute("id"));
            }
            
            // 查找食物卡片，使用實際ID
            WebElement foodCard = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.id("food-" + actualFoodId)
                )
            );
            
            // 捲動到元素位置
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", foodCard);
            Thread.sleep(300);
            
            // 點擊卡片
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", foodCard);
            
            // 等待頁面跳轉
            wait.until(ExpectedConditions.urlContains("/edit"));
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我修改食物名稱為 {string}")
    public void 我修改食物名稱為(String newName) {
        try {
            WebElement nameInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("name"))
            );
            nameInput.clear();
            nameInput.sendKeys(newName);
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我點擊 {string} 按鈕")
    public void 我點擊按鈕(String buttonText) {
        try {
            WebElement button = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(., '" + buttonText + "')] | //button[@type='submit' and contains(., '" + buttonText + "')]")
                )
            );
            
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", button);
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
            
            // 等待頁面跳轉或處理
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我輸入關鍵字 {string} 進行搜尋")
    public void 我輸入關鍵字進行搜尋(String keyword) {
        try {
            // 找到搜尋輸入框
            WebElement searchInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.name("keyword")
                )
            );
            
            searchInput.clear();
            searchInput.sendKeys(keyword);
            
            // 找到搜尋按鈕
            WebElement searchButton = driver.findElement(
                By.xpath("//button[@type='submit' and contains(., '搜尋')]")
            );
            
            searchButton.click();
            
            // 等待頁面載入 - 使用 keyword= 檢查而不是完整編碼後的字串
            wait.until(ExpectedConditions.urlContains("keyword="));
            Thread.sleep(1000); // 給更多時間讓頁面完全載入
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我點擊返回按鈕")
    public void 我點擊返回按鈕() {
        try {
            // 先確認我們在編輯頁面（URL包含 /edit 或有返回列表按鈕）
            Thread.sleep(500); // 等待頁面載入
            
            WebElement backButton = null;
            try {
                // 嘗試在 header 中找返回按鈕
                backButton = driver.findElement(
                    By.xpath("//header//a[contains(@class, 'btn') and contains(., '返回列表')]")
                );
            } catch (NoSuchElementException e1) {
                // 如果找不到，嘗試其他位置
                backButton = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//a[contains(@class, 'btn') and contains(., '返回列表')]")
                    )
                );
            }
            
            if (backButton != null) {
                // 使用 JavaScript 點擊以避免被其他元素遮擋
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", backButton);
                
                Thread.sleep(300);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", backButton);
                
                // 等待頁面跳轉
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我點擊取消按鈕")
    public void 我點擊取消按鈕() {
        try {
            // 等待頁面載入
            Thread.sleep(500);
            
            // 查找取消按鈕（在表單底部，文字為「取消」，class 為 btn-cancel）
            WebElement cancelButton = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(@class, 'btn-cancel') and contains(., '取消')]")
                )
            );
            
            // 使用 JavaScript 點擊以避免被其他元素遮擋
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", cancelButton);
            
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cancelButton);
            
            // 等待頁面跳轉
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @當("我點擊刪除按鈕")
    public void 我點擊刪除按鈕() {
        try {
            // 在編輯頁面找到刪除按鈕（在 header 中的刪除按鈕）
            WebElement deleteButton = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//header//button[contains(@class, 'btn-danger') and contains(., '刪除')]")
                )
            );
            
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", deleteButton);
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", deleteButton);
            
            // 等待瀏覽器原生 confirm 對話框出現
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @那麼("編輯頁面的 URL 應該包含參數 {string} 值為 {string}")
    public void 編輯頁面的URL應該包含參數值為(String paramName, String expectedValue) {
        try {
            // 等待 URL 穩定
            Thread.sleep(500);
            String currentUrl = driver.getCurrentUrl();
            
            // 如果參數是 foodId，需要轉換成實際ID
            if ("foodId".equals(paramName)) {
                long expectedId = Long.parseLong(expectedValue);
                long actualId = SharedWebDriverManager.getActualFoodId(expectedId);
                System.out.println("檢查URL參數: " + paramName + ", 期望ID=" + expectedId + ", 實際ID=" + actualId);
                
                assertTrue(currentUrl.contains(paramName + "=" + actualId), 
                          "URL 應該包含參數 " + paramName + "=" + actualId + ", 實際 URL: " + currentUrl);
            } else {
                // 解析 URL 參數
                assertTrue(currentUrl.contains(paramName + "=" + expectedValue), 
                          "URL 應該包含參數 " + paramName + "=" + expectedValue + ", 實際 URL: " + currentUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @那麼("頁面 URL 應該包含 {string} 錨點")
    public void 頁面URL應該包含錨點(String anchor) {
        try {
            // 等待頁面載入
            Thread.sleep(1000);
            String currentUrl = driver.getCurrentUrl();
            
            // 如果錨點包含food-ID，需要轉換成實際ID
            if (anchor.contains("#food-")) {
                String expectedIdStr = anchor.substring(anchor.indexOf("#food-") + 6);
                long expectedId = Long.parseLong(expectedIdStr);
                long actualId = SharedWebDriverManager.getActualFoodId(expectedId);
                String actualAnchor = "#food-" + actualId;
                System.out.println("檢查錨點: 期望=" + anchor + ", 實際=" + actualAnchor);
                
                assertTrue(currentUrl.contains(actualAnchor), 
                          "URL 應該包含錨點 " + actualAnchor + ", 實際 URL: " + currentUrl);
            } else {
                assertTrue(currentUrl.contains(anchor), 
                          "URL 應該包含錨點 " + anchor + ", 實際 URL: " + currentUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @那麼("頁面應該捲動到 ID 為 {int} 的食物卡片")
    public void 頁面應該捲動到ID為的食物卡片(int foodId) {
        try {
            // 等待捲動完成
            Thread.sleep(1500);
            
            // 獲取實際的食物ID
            long actualFoodId = SharedWebDriverManager.getActualFoodId(foodId);
            System.out.println("檢查捲動: 期望ID=" + foodId + ", 實際ID=" + actualFoodId);
            
            // 查找目標食物卡片
            WebElement foodCard = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.id("food-" + actualFoodId)
                )
            );
            
            // 確認卡片在視口中
            assertTrue(isElementInViewport(foodCard), 
                      "食物卡片 food-" + actualFoodId + " 應該在視口中");
            
            // 檢查是否有高亮效果（可選）
            String cardClass = foodCard.getAttribute("class");
            System.out.println("Food card " + actualFoodId + " classes: " + cardClass);
            
        } catch (Exception e) {
            fail("無法找到或捲動到食物卡片: " + e.getMessage());
        }
    }

    /**
     * 檢查元素是否在視口中
     */
    private boolean isElementInViewport(WebElement element) {
        try {
            Boolean result = (Boolean) ((JavascriptExecutor) driver).executeScript(
                "var elem = arguments[0];" +
                "var rect = elem.getBoundingClientRect();" +
                "return (" +
                "    rect.top >= 0 &&" +
                "    rect.left >= 0 &&" +
                "    rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&" +
                "    rect.right <= (window.innerWidth || document.documentElement.clientWidth)" +
                ");",
                element
            );
            
            // 如果元素不完全在視口中，至少要部分可見
            if (!result) {
                result = (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var elem = arguments[0];" +
                    "var rect = elem.getBoundingClientRect();" +
                    "var windowHeight = window.innerHeight || document.documentElement.clientHeight;" +
                    "var windowWidth = window.innerWidth || document.documentElement.clientWidth;" +
                    "return (" +
                    "    rect.bottom > 0 &&" +
                    "    rect.right > 0 &&" +
                    "    rect.top < windowHeight &&" +
                    "    rect.left < windowWidth" +
                    ");",
                    element
                );
            }
            
            return result != null && result;
        } catch (Exception e) {
            return false;
        }
    }
}
