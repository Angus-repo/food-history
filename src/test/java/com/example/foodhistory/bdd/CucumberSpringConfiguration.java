package com.example.foodhistory.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.example.foodhistory.config.MockOAuth2LoginConfig;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockOAuth2LoginConfig.class)
public class CucumberSpringConfiguration {
    // 這個類提供 Cucumber 與 Spring 整合的配置
    // 所有的 Step Definitions 都會繼承這個 Spring 測試上下文
}
