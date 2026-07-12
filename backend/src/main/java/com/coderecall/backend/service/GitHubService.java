package com.coderecall.backend.service;

import com.coderecall.backend.dto.GitHubContentResponse;
import com.coderecall.backend.dto.GitHubFileRequest;
import com.coderecall.backend.model.OAuthToken;
import com.coderecall.backend.model.Repository;
import com.coderecall.backend.repository.OAuthTokenRepository;
import com.coderecall.backend.repository.RepositoryRepository;
import com.coderecall.backend.security.EncryptionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private final RestClient restClient;
    private final OAuthTokenRepository oAuthTokenRepository;
    private final RepositoryRepository repositoryRepository;
    private final EncryptionUtils encryptionUtils;

    public GitHubService(
            RestClient restClient,
            OAuthTokenRepository oAuthTokenRepository,
            RepositoryRepository repositoryRepository,
            EncryptionUtils encryptionUtils
    ) {
        this.restClient = restClient;
        this.oAuthTokenRepository = oAuthTokenRepository;
        this.repositoryRepository = repositoryRepository;
        this.encryptionUtils = encryptionUtils;
    }

    private String getDecryptedAccessToken(String userId) {
        OAuthToken token = oAuthTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("GitHub account not linked. Please log in again."));
        return encryptionUtils.decrypt(token.getAccessToken());
    }

    public List<Map<String, Object>> fetchUserRepositories(String userId) {
        String token = getDecryptedAccessToken(userId);
        String url = "https://api.github.com/user/repos?per_page=100&sort=updated";

        try {
            return restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String uploadFile(
            String userId,
            String filePath,
            String code,
            String commitMessage
    ) {
        String token = getDecryptedAccessToken(userId);
        Repository activeRepo = repositoryRepository.findByUserIdAndIsActive(userId, true)
                .orElseThrow(() -> new RuntimeException("No active repository configured. Please select a repository in the extension."));

        String encodedContent = Base64.getEncoder()
                .encodeToString(code.getBytes(StandardCharsets.UTF_8));

        String sha = getFileSha(token, activeRepo.getFullName(), filePath, activeRepo.getBranch());

        GitHubFileRequest request = (sha == null)
                ? new GitHubFileRequest(commitMessage, encodedContent, activeRepo.getBranch())
                : new GitHubFileRequest(commitMessage, encodedContent, activeRepo.getBranch(), sha);

        String url = String.format(
                "https://api.github.com/repos/%s/contents/%s",
                activeRepo.getFullName(),
                filePath
        );

        return restClient.put()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .body(request)
                .retrieve()
                .body(String.class);
    }

    private String getFileSha(String token, String repoFullName, String filePath, String branch) {
        try {
            String url = String.format(
                    "https://api.github.com/repos/%s/contents/%s?ref=%s",
                    repoFullName,
                    filePath,
                    branch
            );

            GitHubContentResponse response = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GitHubContentResponse.class);

            return (response != null) ? response.getSha() : null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> createRepository(String userId, String repoName, boolean isPrivate) {
        String token = getDecryptedAccessToken(userId);
        String url = "https://api.github.com/user/repos";

        try {
            Map<String, Object> body = Map.of(
                    "name", repoName,
                    "private", isPrivate,
                    "auto_init", true // Initialize with README to create default branch
            );

            return restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new RuntimeException("Repository name already exists or is invalid on GitHub.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create repository on GitHub: " + e.getMessage(), e);
        }
    }
}
