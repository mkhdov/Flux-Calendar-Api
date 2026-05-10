package com.flux.user_management_service.auth;

import com.flux.user_management_service.auth.dto.ForgotPasswordRequest;
import com.flux.user_management_service.auth.dto.ResetPasswordRequest;
import com.flux.user_management_service.auth.dto.ValidateTokenRequest;
import com.flux.user_management_service.user.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.initiateForgotPassword(request.email());

        return ResponseEntity.ok(new ApiResponse(true,
                "If that email exists, a reset link has been sent."));
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<ApiResponse> validateToken(
            @Valid @RequestBody ValidateTokenRequest request) {

        passwordResetService.validateToken(request.token());
        return ResponseEntity.ok(new ApiResponse(true, "Token is valid"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new ApiResponse(true, "Password updated successfully"));
    }
}
