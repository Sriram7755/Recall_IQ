package com.coderecall.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "repositories")
public class Repository {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String repoName;
    private String fullName;
    private String branch;
    private boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
