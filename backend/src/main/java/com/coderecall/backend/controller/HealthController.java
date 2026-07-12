package com.coderecall.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RestController
public class HealthController {

    public HealthController() {
        System.out.println("HealthController Loaded");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "message", "CodeRecall backend is running!");
    }
}
