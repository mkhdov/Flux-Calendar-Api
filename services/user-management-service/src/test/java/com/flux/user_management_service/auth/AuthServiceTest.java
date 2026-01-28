package com.flux.user_management_service.auth;

import com.flux.user_management_service.security.EmailService;
import com.flux.user_management_service.security.JwtTokenProvider;
import com.flux.user_management_service.user.User;
import com.flux.user_management_service.user.UserRepository;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private com.flux.user_management_service.kafka.UserProducer userProducer;

    @InjectMocks

    private AuthService authService;

    private SignUpRequest signUpRequest;
    private LoginRequest loginRequest;
    private User user;

    @BeforeEach
    void setUp() {
        signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("password")
                .firstName("Test")
                .lastName("User")
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password")
                .build();

        user = new User();
        user.setEmail("test@example.com");
        user.setPassword("encodedPassword");
        user.setVerificationToken("token");
        user.setTokenExpiryDate(LocalDateTime.now().plusHours(24));
    }

    @Test
    void registerUser_ShouldReturnError_WhenEmailExists() {
        when(userRepository.existsByEmail(signUpRequest.getEmail())).thenReturn(true);

        ApiResponse response = authService.registerUser(signUpRequest);

        assertFalse(response.isSuccess());
        assertEquals("Email address already in use.", response.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_ShouldRegisterUser_WhenEmailDoesNotExist() {
        when(userRepository.existsByEmail(signUpRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(signUpRequest.getPassword())).thenReturn("encodedPassword");

        ApiResponse response = authService.registerUser(signUpRequest);

        assertTrue(response.isSuccess());
        assertEquals("User registered successfully. Please check your email to verify your account.",
                response.getMessage());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendVerificationEmail(any(User.class));
    }

    @Test
    void verifyEmail_ShouldReturnError_WhenTokenInvalid() {
        when(userRepository.findByVerificationToken("invalid")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> authService.verifyEmail("invalid"));
        assertEquals("Invalid verification token", exception.getMessage());
    }

    @Test
    void verifyEmail_ShouldReturnError_WhenTokenExpired() {
        user.setTokenExpiryDate(LocalDateTime.now().minusHours(1));
        when(userRepository.findByVerificationToken("token")).thenReturn(Optional.of(user));

        ApiResponse response = authService.verifyEmail("token");

        assertFalse(response.isSuccess());
        assertEquals("Verification token has expired", response.getMessage());
    }

    @Test
    void verifyEmail_ShouldVerifyUser_WhenTokenValid() {
        when(userRepository.findByVerificationToken("token")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        ApiResponse response = authService.verifyEmail("token");

        assertTrue(response.isSuccess());
        assertEquals("Email verified successfully. You can now login.", response.getMessage());
        assertNull(user.getVerificationToken());
        assertNull(user.getTokenExpiryDate());
        verify(userRepository).save(user);
        verify(userProducer).sendUserEvent(any());
        verify(emailService).sendRegistrationSuccessEmail(user);
    }

    @Test
    void authenticateUser_ShouldReturnToken_WhenCredentialsValid() {
        Authentication authentication = mock(Authentication.class);
        com.flux.user_management_service.security.UserPrincipal userPrincipal = mock(
                com.flux.user_management_service.security.UserPrincipal.class);

        when(userPrincipal.isEnabled()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        AuthResponse response = authService.authenticateUser(loginRequest);

        assertNotNull(response);
        assertEquals("jwt-token", response.getAccessToken());
        verify(emailService).sendLoginNotification(user);
    }
}
