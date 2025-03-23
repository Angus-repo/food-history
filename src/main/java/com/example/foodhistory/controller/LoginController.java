package com.example.foodhistory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/login?logout")
    public String logout() {
        return "login";
    }
    
    @GetMapping("/")
    public String home() {
        return "redirect:/foods";
    }
}