package com.example.foodhistory.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoginControllerTest {

    private final LoginController loginController = new LoginController();

    @Test
    public void testLogin() {
        String viewName = loginController.login();
        assertEquals("login", viewName);
    }

    @Test
    public void testLogout() {
        String viewName = loginController.logout();
        assertEquals("login", viewName);
    }

    @Test
    public void testHome() {
        String viewName = loginController.home();
        assertEquals("redirect:/foods", viewName);
    }
}
