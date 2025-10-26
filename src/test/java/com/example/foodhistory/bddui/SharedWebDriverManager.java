package com.example.foodhistory.bddui;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.foodhistory.config.TestSecurityConfig;
import com.example.foodhistory.config.MockOAuth2LoginConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 共享的 WebDriver 管理器，避免每個 Step Definition 類都創建自己的 WebDriver
 * 這可以防止資源耗盡和 ChromeDriver 崩潰問題
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, MockOAuth2LoginConfig.class})
public class SharedWebDriverManager {

    @LocalServerPort
    private int port;

    @Autowired(required = false)
    private OidcUser mockOidcUser;

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;
    private static int testCount = 0;
    
    // ID映射表：期望的ID -> 實際的ID
    private static Map<Long, Long> foodIdMapping = new HashMap<>();

    /**
     * 在所有 @ui 測試開始前初始化 WebDriver
     * order = 0 確保這個 hook 最先執行
     */
    @Before(value = "@ui", order = 0)
    public void setUpWebDriver() {
        testCount++;
        
        // 設置 Mock OAuth2 認證
        setupMockAuthentication();
        
        if (driver == null) {
            System.out.println("\n🚀 正在啟動 WebDriver...");
            
            WebDriverManager.chromedriver().setup();
            
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--lang=zh-TW");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-software-rasterizer");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-backgrounding-occluded-windows");
            options.addArguments("--disable-renderer-backgrounding");
            options.addArguments("--disable-features=TranslateUI,VizDisplayCompositor");
            options.addArguments("--disable-ipc-flooding-protection");
            options.addArguments("--force-device-scale-factor=1");
            options.addArguments("--disable-hang-monitor");
            options.addArguments("--disable-prompt-on-repost");
            options.addArguments("--metrics-recording-only");
            options.addArguments("--enable-automation");
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-logging"});
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
            
            wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            baseUrl = "http://localhost:" + port;
            
            System.out.println("✅ WebDriver 已啟動，測試服務器端口：" + port);
        } else {
            System.out.println("♻️  重用現有 WebDriver（測試 #" + testCount + "）");
        }
    }

    /**
     * 在每個測試場景結束後清理
     * order = 1000 確保這個 hook 在其他 hooks 之後執行
     */
    @After(value = "@ui", order = 1000)
    public void cleanupAfterScenario(Scenario scenario) {
        if (driver != null) {
            try {
                // 清除 cookies 和 local storage，確保下一個測試從乾淨狀態開始
                driver.manage().deleteAllCookies();
                
                // 清除ID映射
                clearFoodIdMapping();
                
                if (scenario.isFailed()) {
                    System.err.println("❌ 測試失敗：" + scenario.getName());
                    // 可以在這裡添加截圖邏輯
                } else {
                    System.out.println("✅ 測試通過：" + scenario.getName());
                }
            } catch (Exception e) {
                System.err.println("⚠️  清理時發生錯誤：" + e.getMessage());
            }
        }
    }

    /**
     * 在所有測試完成後關閉 WebDriver
     * 使用 JVM shutdown hook 確保即使測試中斷也能正確關閉
     */
    @After(value = "@ui", order = 10000)
    public void tearDownWebDriver() {
        testCount--;
        
        // 當計數器歸零時，關閉 WebDriver
        if (testCount == 0 && driver != null) {
            System.out.println("\n🛑 正在關閉 WebDriver...");
            try {
                driver.quit();
                driver = null;
                wait = null;
                baseUrl = null;
                System.out.println("✅ WebDriver 已安全關閉");
            } catch (Exception e) {
                System.err.println("⚠️  關閉 WebDriver 時發生錯誤：" + e.getMessage());
            }
        }
    }

    /**
     * 設置 Mock OAuth2 認證
     */
    private void setupMockAuthentication() {
        if (mockOidcUser != null) {
            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                mockOidcUser,
                mockOidcUser.getAuthorities(),
                "google"
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            System.out.println("🔐 已設置 Mock OAuth2 認證: " + mockOidcUser.getAttribute("email"));
        }
    }

    /**
     * 獲取共享的 WebDriver 實例
     */
    public static WebDriver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("WebDriver 尚未初始化！請確保測試場景有 @ui 標籤");
        }
        return driver;
    }

    /**
     * 獲取共享的 WebDriverWait 實例
     */
    public static WebDriverWait getWait() {
        if (wait == null) {
            throw new IllegalStateException("WebDriverWait 尚未初始化！請確保測試場景有 @ui 標籤");
        }
        return wait;
    }

    /**
     * 獲取測試服務器的基礎 URL
     */
    public static String getBaseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("Base URL 尚未設定！請確保測試場景有 @ui 標籤");
        }
        return baseUrl;
    }
    
    /**
     * 映射期望的食物ID到實際的ID
     */
    public static void mapFoodId(long expectedId, long actualId) {
        foodIdMapping.put(expectedId, actualId);
    }
    
    /**
     * 獲取實際的食物ID
     */
    public static long getActualFoodId(long expectedId) {
        return foodIdMapping.getOrDefault(expectedId, expectedId);
    }
    
    /**
     * 清除ID映射
     */
    public static void clearFoodIdMapping() {
        foodIdMapping.clear();
    }
}
