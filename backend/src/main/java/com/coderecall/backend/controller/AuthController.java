package com.coderecall.backend.controller;

import com.coderecall.backend.model.OAuthToken;
import com.coderecall.backend.model.User;
import com.coderecall.backend.repository.OAuthTokenRepository;
import com.coderecall.backend.security.EncryptionUtils;
import com.coderecall.backend.security.JwtTokenProvider;
import com.coderecall.backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    // In-memory cache for state CSRF validation: stateKey -> StateInfo(extensionId, expiryMillis)
    private static final Map<String, StateInfo> stateCache = new ConcurrentHashMap<>();

    // In-memory cache for short-lived one-time authorization exchange codes: authCode -> CodeInfo(jwtToken, expiryMillis)
    private static final Map<String, CodeInfo> exchangeCodeCache = new ConcurrentHashMap<>();

    private static class StateInfo {
        final String extensionId;
        final long expiryMillis;

        StateInfo(String extensionId, long ttlMs) {
            this.extensionId = extensionId;
            this.expiryMillis = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryMillis;
        }
    }

    private static class CodeInfo {
        final String jwtToken;
        final long expiryMillis;

        CodeInfo(String jwtToken, long ttlMs) {
            this.jwtToken = jwtToken;
            this.expiryMillis = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryMillis;
        }
    }

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthTokenRepository oAuthTokenRepository;
    private final EncryptionUtils encryptionUtils;
    private final RestClient restClient;

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    @Value("${github.extension-redirect-base:https://%s.chromiumapp.org/oauth}")
    private String extensionRedirectBase;

    public AuthController(
            UserService userService,
            JwtTokenProvider jwtTokenProvider,
            OAuthTokenRepository oAuthTokenRepository,
            EncryptionUtils encryptionUtils,
            RestClient restClient
    ) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.oAuthTokenRepository = oAuthTokenRepository;
        this.encryptionUtils = encryptionUtils;
        this.restClient = restClient;
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> getLoginUrl(@RequestParam("extensionId") String extensionId) {
        log.info("Received /api/auth/login request for extensionId: {}", maskId(extensionId));

        if (extensionId == null || extensionId.trim().isEmpty() || !extensionId.matches("^[a-zA-Z0-9_-]{1,64}$")) {
            log.warn("Invalid extensionId received: {}", extensionId);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid extensionId format"));
        }

        cleanExpiredEntries();

        // Generate cryptographically random CSRF state nonce
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        String nonce = HexFormat.of().formatHex(randomBytes);

        String stateKey = nonce + ":" + extensionId;
        // State expires in 5 minutes (300,000 ms)
        stateCache.put(stateKey, new StateInfo(extensionId, 300_000));

        // Use UriComponentsBuilder with .build().encode() for RFC 3986 percent-encoding
        String url = UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "repo,user")
                .queryParam("state", stateKey)
                .build()
                .encode()
                .toUriString();

        log.info("Generated OAuth authorize URL successfully with encoded UriComponentsBuilder and CSRF nonce.");
        return ResponseEntity.ok(Map.of("authUrl", url));
    }

    @GetMapping("/callback")
    public void githubCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String ghError,
            @RequestParam(value = "error_description", required = false) String ghErrorDesc,
            HttpServletResponse response
    ) throws IOException {
        log.info("Received GitHub OAuth callback.");

        if (ghError != null) {
            log.error("GitHub OAuth authorization error returned: {} - {}", ghError, ghErrorDesc);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "GitHub authorization denied: " + (ghErrorDesc != null ? ghErrorDesc : ghError));
            return;
        }

        if (code == null || state == null) {
            log.warn("Callback missing required parameters: code or state.");
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing code or state parameter.");
            return;
        }

        cleanExpiredEntries();

        StateInfo stateInfo = stateCache.remove(state);
        if (stateInfo == null || stateInfo.isExpired()) {
            log.error("State validation failed for state: {}. Invalid or expired state parameter.", maskId(state));
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired state parameter (CSRF protection).");
            return;
        }

        String extensionId = stateInfo.extensionId;
        log.info("State validated successfully for extensionId: {}", maskId(extensionId));

        try {
            // 1. Exchange code for GitHub Access Token
            log.info("Checkpoint 1/5: Exchanging authorization code with GitHub OAuth token endpoint...");
            Map<String, Object> tokenResponse;
            try {
                tokenResponse = restClient.post()
                        .uri("https://github.com/login/oauth/access_token")
                        .header("Accept", "application/json")
                        .body(Map.of(
                                "client_id", clientId,
                                "client_secret", clientSecret,
                                "code", code,
                                "redirect_uri", redirectUri
                        ))
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientResponseException e) {
                log.error("GitHub token exchange HTTP error: status={}, response={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("GitHub token exchange HTTP error: " + e.getStatusCode());
            }

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                String errStr = tokenResponse != null && tokenResponse.containsKey("error_description") 
                        ? String.valueOf(tokenResponse.get("error_description")) 
                        : "No access token in response";
                log.error("GitHub token exchange failed: {}", errStr);
                throw new RuntimeException("Failed to exchange code for access token: " + errStr);
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String tokenType = (String) tokenResponse.get("token_type");
            String scope = (String) tokenResponse.get("scope");
            log.info("Checkpoint 1/5 SUCCESS: GitHub access token obtained.");

            // 2. Fetch GitHub User Profile
            log.info("Checkpoint 2/5: Fetching user profile from GitHub API...");
            Map<String, Object> userProfile;
            try {
                userProfile = restClient.get()
                        .uri("https://api.github.com/user")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github+json")
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientResponseException e) {
                log.error("GitHub user profile fetch HTTP error: status={}", e.getStatusCode());
                throw new RuntimeException("Failed to fetch GitHub user profile: " + e.getStatusCode());
            }

            if (userProfile == null || !userProfile.containsKey("id")) {
                log.error("GitHub user profile response is empty or missing ID field.");
                throw new RuntimeException("Failed to fetch user profile from GitHub");
            }

            String githubId = String.valueOf(userProfile.get("id"));
            String username = (String) userProfile.get("login");
            String avatarUrl = (String) userProfile.get("avatar_url");
            String email = (String) userProfile.get("email");
            if (email == null) {
                email = username + "@users.noreply.github.com";
            }
            log.info("Checkpoint 2/5 SUCCESS: Retrieved profile for GitHub user: {}", username);

            // 3. Upsert User in MongoDB
            log.info("Checkpoint 3/5: Saving user profile in MongoDB...");
            User user = userService.processOAuthUser(githubId, username, email, avatarUrl);
            log.info("Checkpoint 3/5 SUCCESS: User persisted with internal ID: {}", user.getId());

            // 4. Encrypt and Save OAuth Token in MongoDB
            log.info("Checkpoint 4/5: Encrypting and saving OAuth token in MongoDB...");
            OAuthToken oAuthToken = oAuthTokenRepository.findByUserId(user.getId())
                    .orElse(new OAuthToken());
            oAuthToken.setUserId(user.getId());
            oAuthToken.setAccessToken(encryptionUtils.encrypt(accessToken));
            oAuthToken.setTokenType(tokenType);
            oAuthToken.setScope(scope);
            oAuthToken.setUpdatedAt(LocalDateTime.now());
            if (oAuthToken.getId() == null) {
                oAuthToken.setCreatedAt(LocalDateTime.now());
            }
            oAuthTokenRepository.save(oAuthToken);
            log.info("Checkpoint 4/5 SUCCESS: OAuth token encrypted and stored.");

            // 5. Generate JWT token and single-use auth exchange code
            log.info("Checkpoint 5/5: Generating JWT token and single-use authorization exchange code...");
            String jwtToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

            String authCode = UUID.randomUUID().toString();
            // Exchange code valid for 60 seconds (60,000 ms)
            exchangeCodeCache.put(authCode, new CodeInfo(jwtToken, 60_000));

            // Format redirect URL back to chromiumapp.org using UriComponentsBuilder
            String chromeRedirectUri = String.format(extensionRedirectBase, extensionId);
            String redirectUrl = UriComponentsBuilder.fromHttpUrl(chromeRedirectUri)
                    .queryParam("code", authCode)
                    .build()
                    .encode()
                    .toUriString();
                    
            log.info("OAuth Flow Complete. Redirecting client to Chrome Extension callback.");

            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("OAuth callback processing failed:", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/exchange")
    public ResponseEntity<Map<String, Object>> exchangeCode(@RequestBody Map<String, String> request) {
        String code = request != null ? request.get("code") : null;
        log.info("Received /api/auth/exchange request for code: {}", maskId(code));

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authorization exchange code is required."
            ));
        }

        cleanExpiredEntries();

        CodeInfo codeInfo = exchangeCodeCache.remove(code); // Single-use! Immediately remove.
        if (codeInfo == null || codeInfo.isExpired()) {
            log.warn("Invalid or expired exchange code attempted: {}", maskId(code));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Invalid or expired authorization exchange code."
            ));
        }

        log.info("Exchange code successfully validated and consumed. Returning JWT token.");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "token", codeInfo.jwtToken
        ));
    }

    private String maskId(String id) {
        if (id == null) return "null";
        if (id.length() <= 8) return "***";
        return id.substring(0, 4) + "..." + id.substring(id.length() - 4);
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"success\":false,\"error\":\"%s\"}", message.replace("\"", "\\\"")));
    }

    private void cleanExpiredEntries() {
        stateCache.entrySet().removeIf(e -> e.getValue().isExpired());
        exchangeCodeCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}