package com.coderecall.backend.service;

import com.coderecall.backend.model.Submission;
import com.coderecall.backend.repository.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final GitHubService gitHubService;
    private final TopicClassificationService topicClassificationService;
    private final LlmService llmService;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            GitHubService gitHubService,
            TopicClassificationService topicClassificationService,
            LlmService llmService
    ) {
        this.submissionRepository = submissionRepository;
        this.gitHubService = gitHubService;
        this.topicClassificationService = topicClassificationService;
        this.llmService = llmService;
    }

    private String resolveTopicWithLlmFallback(Submission submission) {
        try {
            String topic = llmService.classifyTopic(
                    submission.getTitle(),
                    submission.getDescription(),
                    submission.getLeetcodeTopics()
            );
            if (topic != null && !topic.isEmpty()) {
                topic = topic.trim().replaceAll("^\"|\"$", "");
                if (!topic.isEmpty()) {
                    return Character.toUpperCase(topic.charAt(0)) + topic.substring(1);
                }
            }
        } catch (Exception e) {
            System.err.println("LLM classification failed, falling back to local tags: " + e.getMessage());
        }
        return topicClassificationService.resolveTopic(submission.getLeetcodeTopics());
    }

    public Submission saveSubmission(String userId, Submission submission) {
        submission.setUserId(userId);

        // 1. Check for duplicate submissions (same problem and same language for this user)
        Optional<Submission> existingOpt = submissionRepository.findByUserIdAndProblemIdAndLanguage(
                userId,
                submission.getProblemId(),
                submission.getLanguage()
        );

        if (existingOpt.isPresent()) {
            Submission existing = existingOpt.get();
            // If the code is identical, return immediately to prevent redundant GitHub pushes
            if (existing.getCode().trim().equals(submission.getCode().trim())) {
                existing.setIgnoredDuplicate(true);
                return existing;
            }
            
            // If code is different, update the existing entry
            existing.setCode(submission.getCode());
            existing.setCreatedAt(LocalDateTime.now());
            existing.setDescription(submission.getDescription());
            existing.setLeetcodeTopics(submission.getLeetcodeTopics());
            existing.setDifficulty(submission.getDifficulty());
            existing.setTitle(submission.getTitle());
            existing.setSlug(submission.getSlug());
            
            // Re-resolve topic via LLM
            String topic = resolveTopicWithLlmFallback(submission);
            existing.setTopic(topic);
            
            String extension = getFileExtension(existing.getLanguage());
            String filePath = constructFilePath(topic, existing.getProblemId(), existing.getSlug(), existing.getTitle(), extension);
            existing.setRepoPath(filePath);

            // Push update to GitHub
            gitHubService.uploadFile(
                    userId,
                    filePath,
                    existing.getCode(),
                    "Update solution for " + existing.getTitle() + " (" + existing.getLanguage() + ")"
            );

            return submissionRepository.save(existing);
        }

        // 2. If new submission, proceed with fresh insertion
        submission.setCreatedAt(LocalDateTime.now());

        String topic = resolveTopicWithLlmFallback(submission);
        submission.setTopic(topic);

        String extension = getFileExtension(submission.getLanguage());
        String filePath = constructFilePath(topic, submission.getProblemId(), submission.getSlug(), submission.getTitle(), extension);
        submission.setRepoPath(filePath);

        // Save metadata to MongoDB first
        Submission savedSubmission = submissionRepository.save(submission);

        // Push to GitHub
        try {
            gitHubService.uploadFile(
                    userId,
                    filePath,
                    savedSubmission.getCode(),
                    "Add solution for " + savedSubmission.getTitle() + " (" + savedSubmission.getLanguage() + ")"
            );
        } catch (Exception e) {
            // In a production app, we would log this and possibly throw or handle
            throw new RuntimeException("Failed to sync solution to GitHub: " + e.getMessage(), e);
        }

        return savedSubmission;
    }

    public List<Submission> getAllSubmissions() {
        return submissionRepository.findAll();
    }

    private String constructFilePath(String topic, String problemId, String slug, String title, String extension) {
        String finalSlug = slug;
        if (finalSlug == null || finalSlug.isEmpty()) {
            finalSlug = title.toLowerCase().replace(" ", "-");
        }

        // Format slug to Capitalized-Words-With-Dashes (e.g. rotate-list -> Rotate-List)
        String capitalizedSlug = Arrays.stream(finalSlug.split("-"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining("-"));

        String filename = problemId + "-" + capitalizedSlug + extension;
        return "LeetCode/" + topic + "/" + filename;
    }

    private String getFileExtension(String language) {
        if (language == null) return ".txt";
        return switch (language) {
            case "Java" -> ".java";
            case "Python", "Python3" -> ".py";
            case "C++" -> ".cpp";
            case "JavaScript" -> ".js";
            case "TypeScript" -> ".ts";
            case "C#" -> ".cs";
            case "C" -> ".c";
            case "Go" -> ".go";
            case "Kotlin" -> ".kt";
            case "Swift" -> ".swift";
            case "Rust" -> ".rs";
            case "Ruby" -> ".rb";
            case "PHP" -> ".php";
            case "Dart" -> ".dart";
            case "Scala" -> ".scala";
            case "Elixir" -> ".ex";
            case "Erlang" -> ".erl";
            case "Racket" -> ".rkt";
            default -> ".txt";
        };
    }
}
