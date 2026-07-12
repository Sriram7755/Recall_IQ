package com.coderecall.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github-test")
public class GitHubController {

    @GetMapping("/ping")
    public String ping() {
        return "GitHub integration endpoint active. Auth needed for other endpoints.";
    }
}