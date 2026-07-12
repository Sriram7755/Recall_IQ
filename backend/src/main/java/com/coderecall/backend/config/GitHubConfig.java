package com.coderecall.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubConfig {

    @Value("${github.token:}")
    private String token;

    @Value("${github.owner:}")
    private String owner;

    @Value("${github.repo:}")
    private String repo;

    @Value("${github.branch}")
    private String branch;

    public String getToken() { return token; }
    public String getOwner() { return owner; }
    public String getRepo() { return repo; }
    public String getBranch() { return branch; }
}