package com.flux.user_management_service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.user_management_service.auth.dto.ForgotPasswordRequest;
import com.flux.user_management_service.auth.dto.ResetPasswordRequest;
import com.flux.user_management_service.auth.dto.ValidateTokenRequest;
import com.flux.user_management_service.user.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController passwordResetController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void forgotPassword_ShouldReturn200_WhenEmailProvided() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
        doNothing().when(passwordResetService).initiateForgotPassword("test@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If that email exists, a reset link has been sent."));
    }

    @Test
    void validateToken_ShouldReturn200_WhenTokenIsValid() throws Exception {
        ValidateTokenRequest request = new ValidateTokenRequest("valid-token");
        doNothing().when(passwordResetService).validateToken("valid-token");

        mockMvc.perform(post("/api/auth/validate-reset-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    void resetPassword_ShouldReturn200_WhenRequestIsValid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "newPassword123");
        doNothing().when(passwordResetService).resetPassword("valid-token", "newPassword123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }
}
