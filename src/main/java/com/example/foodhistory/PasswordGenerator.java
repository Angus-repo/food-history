package com.example.foodhistory;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Scanner;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        Scanner scanner = new Scanner(System.in);

        System.out.print("請輸入你的新密碼: ");
        String rawPassword = scanner.nextLine();

        String encodedPassword = passwordEncoder.encode(rawPassword);

        System.out.println("加密後的密碼: " + encodedPassword);
        scanner.close();
    }
}
