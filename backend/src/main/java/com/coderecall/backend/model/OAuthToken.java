package com.coderecall.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "oauth_tokens")
public class OAuthToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String accessToken; // Encrypted before persist
    private String tokenType;
    private String scope;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
