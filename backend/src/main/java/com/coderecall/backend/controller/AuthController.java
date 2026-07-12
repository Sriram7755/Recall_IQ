package com.coderecall.backend.controller;

import com.coderecall.backend.model.User;
import com.coderecall.backend.model.OAuthToken;
import com.coderecall.backend.repository.OAuthTokenRepository;
import com.coderecall.backend.security.EncryptionUtils;
import com.coderecall.backend.security.JwtTokenProvider;
import com.coderecall.backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

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

    @Value("${github.extension-redirect-base}")
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
        // redirect_uri must match what's registered in GitHub OAuth App (localhost callback)
        // state carries the extensionId so the callback can build the chromiumapp.org redirect
        String url = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=repo,user&state=%s",
                clientId, redirectUri, extensionId
        );
        return ResponseEntity.ok(Map.of("authUrl", url));
    }

    @GetMapping("/callback")
    public void githubCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String extensionId,
            HttpServletResponse response
    ) throws IOException {
        try {
            // 1. Exchange code for GitHub Access Token
            Map<String, Object> tokenResponse = restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code,
                            "redirect_uri", redirectUri  // must match the authorize step
                    ))
                    .retrieve()
                    .body(Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new RuntimeException("Failed to exchange code for access token");
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String tokenType = (String) tokenResponse.get("token_type");
            String scope = (String) tokenResponse.get("scope");

            // 2. Fetch GitHub User Profile
            Map<String, Object> userProfile = restClient.get()
                    .uri("https://api.github.com/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);

            if (userProfile == null || !userProfile.containsKey("id")) {
                throw new RuntimeException("Failed to fetch user profile from GitHub");
            }

            String githubId = String.valueOf(userProfile.get("id"));
            String username = (String) userProfile.get("login");
            String avatarUrl = (String) userProfile.get("avatar_url");
            String email = (String) userProfile.get("email");
            if (email == null) {
                email = username + "@users.noreply.github.com";
            }

            // 3. Upsert User in MongoDB
            User user = userService.processOAuthUser(githubId, username, email, avatarUrl);

            // 4. Encrypt and Save OAuth Token
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

            // 5. Generate JWT token
            String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

            // 6. Redirect back to chromiumapp.org URL so launchWebAuthFlow intercepts it
            String chromeRedirectUri = String.format(extensionRedirectBase, extensionId);
            String redirectUrl = String.format("%s?token=%s", chromeRedirectUri, token);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed: " + e.getMessage());
        }
    }
}
