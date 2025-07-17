package com.example.foodhistory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.angus.spring.aspect.LoggingAspect;

@SpringBootApplication
@ComponentScan(basePackageClasses = {LoggingAspect.class, FoodHistoryApplication.class})
public class FoodHistoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodHistoryApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {
            // String adminEmail = System.getenv("ADMIN_EMAIL");
            String adminEmail = "lh0528@gmail.com";
            if (adminEmail == null) {
                System.err.println("ADMIN_EMAIL 環境變數未設置，無法初始化 admin 帳號。");
            } else {
                System.out.println("Admin Google 帳號已設定為: " + adminEmail);
            }
        };
    }
}