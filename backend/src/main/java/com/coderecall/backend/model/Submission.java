package com.coderecall.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "submissions")
@CompoundIndex(name = "user_problem_lang_idx", def = "{'userId': 1, 'problemId': 1, 'language': 1}", unique = true)
public class Submission {

    @Id
    private String id;

    private String userId;

    private String problemId;

    private String title;

    private String slug;

    private String difficulty;

    private String language;

    private String platform;

    private String code;

    private String topic;
    
    private String description;

    private List<String> leetcodeTopics;
    
    private String sha; // Commit SHA returned from GitHub
    private String repoPath; // Destination path in the repository

    @org.springframework.data.annotation.Transient
    private Boolean ignoredDuplicate;

    private LocalDateTime createdAt;
}