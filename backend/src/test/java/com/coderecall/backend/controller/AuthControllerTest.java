package com.coderecall.backend.controller;

import com.coderecall.backend.repository.OAuthTokenRepository;
import com.coderecall.backend.security.EncryptionUtils;
import com.coderecall.backend.security.JwtTokenProvider;
import com.coderecall.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthTokenRepository oAuthTokenRepository;

    @Mock
    private EncryptionUtils encryptionUtils;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(authController, "clientId", "test-client-id");
        ReflectionTestUtils.setField(authController, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(authController, "redirectUri", "http://localhost:8080/api/auth/callback");
        ReflectionTestUtils.setField(authController, "extensionRedirectBase", "https://%s.chromiumapp.org/oauth");
    }

    @Test
    void testGetLoginUrl_ValidExtensionId() {
        String extensionId = "mfnnhomhimlnghjcfgjkoobgakiinnge";
        ResponseEntity<Map<String, String>> response = authController.getLoginUrl(extensionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("authUrl"));

        String authUrl = response.getBody().get("authUrl");
        assertTrue(authUrl.contains("client_id=test-client-id"));
        assertTrue(authUrl.contains("redirect_uri=http://localhost:8080/api/auth/callback"));
        assertTrue(authUrl.contains("state="));
        assertTrue(authUrl.contains(extensionId));
    }

    @Test
    void testGetLoginUrl_InvalidExtensionId() {
        ResponseEntity<Map<String, String>> response = authController.getLoginUrl("");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testExchangeCode_MissingCode() {
        ResponseEntity<Map<String, Object>> response = authController.exchangeCode(Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void testExchangeCode_InvalidCode() {
        ResponseEntity<Map<String, Object>> response = authController.exchangeCode(Map.of("code", "non-existent-code"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("success"));
    }
}
