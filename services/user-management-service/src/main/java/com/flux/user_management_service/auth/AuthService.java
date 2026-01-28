package com.flux.user_management_service.auth;

import com.flux.user_management_service.security.EmailService;
import com.flux.user_management_service.security.JwtTokenProvider;
import com.flux.user_management_service.security.UserPrincipal;
import com.flux.user_management_service.user.User;
import com.flux.user_management_service.user.UserRepository;
import com.flux.user_management_service.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailService emailService;

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider tokenProvider;

    private final com.flux.user_management_service.kafka.UserProducer userProducer;

    public ApiResponse registerUser(SignUpRequest signUpRequest) {

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return new ApiResponse(false, "Email address already in use.");
        }

        User user = new User();
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setFirstname(signUpRequest.getFirstName());
        user.setLastname(signUpRequest.getLastName());
        user.setBirthday(signUpRequest.getBirthday());
        user.setProvider(AuthProvider.LOCAL);
        user.setEnabled(false);

        // Generate verification token
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(LocalDateTime.now().plusHours(24));

        userRepository.save(user);

        emailService.sendVerificationEmail(user);

        return new ApiResponse(true, "User registered successfully. Please check your email to verify your account.");
    }

    public ApiResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (user.getTokenExpiryDate().isBefore(LocalDateTime.now())) {
            return new ApiResponse(false, "Verification token has expired");
        }

        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setTokenExpiryDate(null);
        user = userRepository.save(user);

        userProducer.sendUserEvent(com.flux.user_management_service.kafka.UserEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .type("CREATED")
                .build());

        emailService.sendRegistrationSuccessEmail(user);

        return new ApiResponse(true, "Email verified successfully. You can now login.");

    }

    public AuthResponse authenticateUser(LoginRequest loginRequest) {
        try {
            // This will throw DisabledException if account is not enabled
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()));

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            if (!userPrincipal.isEnabled()) {
                throw new DisabledException("Please verify your email");
            }

            // Generate JWT token only if authentication is successful
            String token = tokenProvider.generateToken(authentication);

            // Send login notification
            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            emailService.sendLoginNotification(user);

            return new AuthResponse(token);

        } catch (DisabledException e) {
            throw new RuntimeException("Your account is not verified. Please check your email to verify your account.");
        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid email or password");
        }
    }

    public ApiResponse resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isEnabled()) {
            return new ApiResponse(false, "Account is already verified.");
        }

        // Generate new verification token
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        // Send verification email
        emailService.sendVerificationEmail(user);

        return new ApiResponse(true, "Verification email sent successfully. Please check your email.");
    }
}