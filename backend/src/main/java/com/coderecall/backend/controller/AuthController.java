
package com.coderecall.backend.controller;

import com.coderecall.backend.model.User;
import com.coderecall.backend.model.OAuthToken;
import com.coderecall.backend.repository.OAuthTokenRepository;
import com.coderecall.backend.security.EncryptionUtils;
import com.coderecall.backend.security.JwtTokenProvider;
import com.coderecall.backend.service.UserService;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

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

    // ==========================================
    // 1. Generate GitHub Login URL
    // ==========================================

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> getLoginUrl(
            @RequestParam("extensionId") String extensionId
    ) {

        String url = UriComponentsBuilder
                .fromUriString(
                        "https://github.com/login/oauth/authorize"
                )
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "repo,user")
                .queryParam("state", extensionId)
                .build()
                .encode()
                .toUriString();

        return ResponseEntity.ok(
                Map.of("authUrl", url)
        );
    }

    // ==========================================
    // 2. GitHub OAuth Callback
    // ==========================================

    @GetMapping("/callback")
    public void githubCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String extensionId,
            HttpServletResponse response
    ) throws IOException {

        try {

            log.info("🔐 GitHub OAuth callback started");

            // ==========================================
            // 3. Exchange Code for Access Token
            // ==========================================

            Map<String, Object> tokenResponse =
                    restClient.post()
                            .uri(
                                    "https://github.com/login/oauth/access_token"
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .body(
                                    Map.of(
                                            "client_id",
                                            clientId,

                                            "client_secret",
                                            clientSecret,

                                            "code",
                                            code,

                                            "redirect_uri",
                                            redirectUri
                                    )
                            )
                            .retrieve()
                            .body(Map.class);

            if (
                    tokenResponse == null ||
                    !tokenResponse.containsKey("access_token")
            ) {

                log.error(
                        "❌ GitHub token exchange failed: {}",
                        tokenResponse
                );

                throw new RuntimeException(
                        "Failed to exchange GitHub authorization code"
                );
            }

            String accessToken =
                    (String) tokenResponse.get("access_token");

            String tokenType =
                    (String) tokenResponse.get("token_type");

            String scope =
                    (String) tokenResponse.get("scope");

            log.info("✅ GitHub access token received");

            // ==========================================
            // 4. Fetch GitHub User Profile
            // ==========================================

            Map<String, Object> userProfile =
                    restClient.get()
                            .uri("https://api.github.com/user")
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "Accept",
                                    "application/vnd.github+json"
                            )
                            .retrieve()
                            .body(Map.class);

            if (
                    userProfile == null ||
                    !userProfile.containsKey("id")
            ) {

                throw new RuntimeException(
                        "Failed to fetch GitHub user profile"
                );
            }

            String githubId =
                    String.valueOf(userProfile.get("id"));

            String username =
                    (String) userProfile.get("login");

            String avatarUrl =
                    (String) userProfile.get("avatar_url");

            String email =
                    (String) userProfile.get("email");

            if (email == null) {
                email = username +
                        "@users.noreply.github.com";
            }

            log.info(
                    "👤 GitHub user authenticated: {}",
                    username
            );

            // ==========================================
            // 5. Save / Update User in MongoDB
            // ==========================================

            User user =
                    userService.processOAuthUser(
                            githubId,
                            username,
                            email,
                            avatarUrl
                    );

            log.info(
                    "✅ User saved successfully: {}",
                    user.getId()
            );

            // ==========================================
            // 6. Encrypt and Save OAuth Token
            // ==========================================

            OAuthToken oAuthToken =
                    oAuthTokenRepository
                            .findByUserId(user.getId())
                            .orElse(new OAuthToken());

            oAuthToken.setUserId(user.getId());

            oAuthToken.setAccessToken(
                    encryptionUtils.encrypt(accessToken)
            );

            oAuthToken.setTokenType(tokenType);

            oAuthToken.setScope(scope);

            oAuthToken.setUpdatedAt(
                    LocalDateTime.now()
            );

            if (oAuthToken.getId() == null) {
                oAuthToken.setCreatedAt(
                        LocalDateTime.now()
                );
            }

            oAuthTokenRepository.save(oAuthToken);

            log.info("✅ OAuth token saved securely");

            // ==========================================
            // 7. Generate Application JWT
            // ==========================================

            String jwt =
                    jwtTokenProvider.generateToken(
                            user.getId(),
                            user.getUsername()
                    );

            log.info("✅ JWT generated successfully");

            // ==========================================
            // 8. Redirect to Chrome Extension
            // ==========================================

            String chromeRedirectUri =
                    String.format(
                            extensionRedirectBase,
                            extensionId
                    );

            String redirectUrl =
                    UriComponentsBuilder
                            .fromUriString(chromeRedirectUri)
                            .queryParam("token", jwt)
                            .build()
                            .encode()
                            .toUriString();

            log.info(
                    "🚀 Redirecting to Chrome extension"
            );

            response.sendRedirect(redirectUrl);

        } catch (Exception e) {

            log.error(
                    "❌ GitHub OAuth callback failed",
                    e
            );

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Authentication failed"
            );
        }
    }
}