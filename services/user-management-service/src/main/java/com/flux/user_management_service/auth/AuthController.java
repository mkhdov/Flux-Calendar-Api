package com.flux.user_management_service.auth;

import com.flux.user_management_service.auth.dto.ChangePasswordRequest;
import com.flux.user_management_service.auth.dto.ResendVerificationRequest;
import com.flux.user_management_service.exception.FieldIsEmptyValidatorException;
import com.flux.user_management_service.security.UserPrincipal;
import com.flux.user_management_service.user.UserService;
import com.flux.user_management_service.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpRequest signUpRequest) {
        try {
            ApiResponse response = authService.registerUser(signUpRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Not authenticated"));
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserResponse user = userService.getUserById(principal.getId());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {

        ApiResponse response = authService.verifyEmail(token);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse response = authService.authenticateUser(loginRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationEmail(@RequestBody ResendVerificationRequest request) {
        try {
            ApiResponse response = authService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }



    @PutMapping("/change-password/{userId}")
    public ResponseEntity<String> changePassword(
            @PathVariable String userId,
            @RequestBody ChangePasswordRequest request){
        authService.changeOldPassword(request, userId);
        return ResponseEntity.ok("Password is modified successfully!");
    }
}