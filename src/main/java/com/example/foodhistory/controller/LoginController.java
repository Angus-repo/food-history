package com.example.foodhistory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    
    @GetMapping("/")
    public String home() {
        return "redirect:/foods";
    }
    
    @GetMapping("/login")
    public String login() {
        // 這個頁面只在用戶未登錄時顯示，提供 OAuth2 登錄選項
        return "login";
    }
}