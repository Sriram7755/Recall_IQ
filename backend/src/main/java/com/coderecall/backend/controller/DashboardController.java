package com.coderecall.backend.controller;

import com.coderecall.backend.model.User;
import com.coderecall.backend.model.Repository;
import com.coderecall.backend.model.Submission;
import com.coderecall.backend.repository.UserRepository;
import com.coderecall.backend.repository.RepositoryRepository;
import com.coderecall.backend.repository.SubmissionRepository;
import com.coderecall.backend.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class DashboardController {

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final SubmissionRepository submissionRepository;

    public DashboardController(
            UserRepository userRepository,
            RepositoryRepository repositoryRepository,
            SubmissionRepository submissionRepository
    ) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.submissionRepository = submissionRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String userId = userPrincipal.getId();

        List<Submission> submissions = submissionRepository.findByUserId(userId);

        // Map each unique problemId to its difficulty to avoid double-counting different languages
        Map<String, String> problemToDifficultyMap = submissions.stream()
                .filter(s -> s.getProblemId() != null && s.getDifficulty() != null)
                .collect(Collectors.toMap(
                        Submission::getProblemId,
                        Submission::getDifficulty,
                        (diff1, diff2) -> diff1 // Keep the first language's difficulty if solved in multiple
                ));

        long totalSolved = problemToDifficultyMap.size();
        long easyCount = problemToDifficultyMap.values().stream().filter(d -> "Easy".equalsIgnoreCase(d)).count();
        long mediumCount = problemToDifficultyMap.values().stream().filter(d -> "Medium".equalsIgnoreCase(d)).count();
        long hardCount = problemToDifficultyMap.values().stream().filter(d -> "Hard".equalsIgnoreCase(d)).count();

        // Map each unique problemId to its topic to avoid topic double-counting
        Map<String, String> problemToTopicMap = submissions.stream()
                .filter(s -> s.getProblemId() != null && s.getTopic() != null)
                .collect(Collectors.toMap(
                        Submission::getProblemId,
                        Submission::getTopic,
                        (topic1, topic2) -> topic1
                ));

        Map<String, Long> topicDistribution = problemToTopicMap.values().stream()
                .collect(Collectors.groupingBy(topic -> topic, Collectors.counting()));

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalSolved", totalSolved);
        dashboard.put("easyCount", easyCount);
        dashboard.put("mediumCount", mediumCount);
        dashboard.put("hardCount", hardCount);
        dashboard.put("topicDistribution", topicDistribution);

        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/submissions/recent")
    public ResponseEntity<List<Submission>> getRecentSubmissions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        List<Submission> recent = submissionRepository.findByUserIdOrderByCreatedAtDesc(
                userPrincipal.getId(),
                PageRequest.of(0, limit)
        );
        return ResponseEntity.ok(recent);
    }

    @GetMapping("/topics")
    public ResponseEntity<List<String>> getTopicsSolved(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Submission> submissions = submissionRepository.findByUserId(userPrincipal.getId());
        List<String> distinctTopics = submissions.stream()
                .map(Submission::getTopic)
                .filter(topic -> topic != null && !topic.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(distinctTopics);
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Repository> repoOpt = repositoryRepository.findByUserId(userPrincipal.getId());

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("repoConnected", repoOpt.isPresent());
        profile.put("activeRepo", repoOpt.map(Repository::getFullName).orElse(null));

        return ResponseEntity.ok(profile);
    }
}
