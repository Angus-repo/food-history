package com.example.foodhistory;

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
}