package com.coderecall.backend.controller;

import com.coderecall.backend.model.Repository;
import com.coderecall.backend.repository.RepositoryRepository;
import com.coderecall.backend.security.UserPrincipal;
import com.coderecall.backend.service.GitHubService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
@CrossOrigin("*")
public class RepositoryController {

    private final GitHubService gitHubService;
    private final RepositoryRepository repositoryRepository;

    public RepositoryController(GitHubService gitHubService, RepositoryRepository repositoryRepository) {
        this.gitHubService = gitHubService;
        this.repositoryRepository = repositoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getUserRepositories(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Map<String, Object>> repos = gitHubService.fetchUserRepositories(userPrincipal.getId());
        return ResponseEntity.ok(repos);
    }

    @PostMapping("/select")
    public ResponseEntity<Map<String, Object>> selectRepository(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request
    ) {
        String repoName = request.get("repoName");
        String fullName = request.get("fullName");
        String branch = request.get("branch");

        if (fullName == null || fullName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repository full name is required."
            ));
        }

        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }

        Repository repository = repositoryRepository.findByUserId(userPrincipal.getId())
                .orElse(new Repository());

        repository.setUserId(userPrincipal.getId());
        repository.setRepoName(repoName);
        repository.setFullName(fullName);
        repository.setBranch(branch);
        repository.setActive(true);
        repository.setUpdatedAt(LocalDateTime.now());
        if (repository.getId() == null) {
            repository.setCreatedAt(LocalDateTime.now());
        }

        repositoryRepository.save(repository);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Repository configured successfully as active tracker."
        ));
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createRepository(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, Object> request
    ) {
        String repoName = (String) request.get("repoName");
        Boolean isPrivate = (Boolean) request.get("private");

        if (repoName == null || repoName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repository name is required."
            ));
        }
        if (isPrivate == null) {
            isPrivate = true; // Default to private for privacy
        }

        try {
            // 1. Create repository on GitHub
            Map<String, Object> githubResponse = gitHubService.createRepository(
                    userPrincipal.getId(),
                    repoName.trim(),
                    isPrivate
            );

            String fullName = (String) githubResponse.get("full_name");
            String defaultBranch = (String) githubResponse.get("default_branch");
            if (defaultBranch == null || defaultBranch.isEmpty()) {
                defaultBranch = "main";
            }

            // 2. Set this repo as active for solution synchronization
            Repository repository = repositoryRepository.findByUserId(userPrincipal.getId())
                    .orElse(new Repository());

            repository.setUserId(userPrincipal.getId());
            repository.setRepoName(repoName.trim());
            repository.setFullName(fullName);
            repository.setBranch(defaultBranch);
            repository.setActive(true);
            repository.setUpdatedAt(LocalDateTime.now());
            if (repository.getId() == null) {
                repository.setCreatedAt(LocalDateTime.now());
            }

            repositoryRepository.save(repository);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fullName", fullName,
                    "message", "Repository created and configured successfully."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
