package com.flux.user_management_service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.user_management_service.user.dto.ApiResponse;
import com.flux.user_management_service.user.dto.AuthResponse;
import com.flux.user_management_service.user.dto.LoginRequest;
import com.flux.user_management_service.user.dto.SignUpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void registerUser_ShouldReturnOk_WhenRegistrationSuccessful() throws Exception {
        SignUpRequest signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("password")
                .firstName("Test")
                .lastName("User")
                .build();

        ApiResponse apiResponse = new ApiResponse(true, "User registered successfully");

        when(authService.registerUser(any(SignUpRequest.class))).thenReturn(apiResponse);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void verifyEmail_ShouldReturnOk_WhenVerificationSuccessful() throws Exception {
        String token = "valid-token";
        ApiResponse apiResponse = new ApiResponse(true, "Email verified successfully");

        when(authService.verifyEmail(token)).thenReturn(apiResponse);

        mockMvc.perform(get("/api/auth/verify")
                .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    void authenticateUser_ShouldReturnToken_WhenLoginSuccessful() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password")
                .build();

        AuthResponse authResponse = new AuthResponse("jwt-token");

        when(authService.authenticateUser(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }
}
