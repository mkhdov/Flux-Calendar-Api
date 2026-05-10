package com.flux.user_management_service.auth;

import com.flux.user_management_service.exception.InvalidTokenException;
import com.flux.user_management_service.security.EmailService;
import com.flux.user_management_service.user.User;
import com.flux.user_management_service.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User user;
    private PasswordResetToken validToken;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user-1");
        user.setEmail("test@example.com");
        user.setPassword("encodedOldPassword");

        validToken = new PasswordResetToken();
        validToken.setId(1L);
        validToken.setToken("valid-token-value");
        validToken.setUser(user);
        validToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        validToken.setUsed(false);
        validToken.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void initiateForgotPassword_ShouldCreateTokenAndSendEmail_WhenEmailExists() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));

        passwordResetService.initiateForgotPassword("test@example.com");

        verify(tokenRepository).deleteAllByUser(user);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), anyString());
    }

    @Test
    void initiateForgotPassword_ShouldDoNothing_WhenEmailDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        passwordResetService.initiateForgotPassword("unknown@example.com");

        verify(tokenRepository, never()).deleteAllByUser(any());
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void validateToken_ShouldSucceed_WhenTokenIsValid() {
        when(tokenRepository.findByToken("valid-token-value")).thenReturn(Optional.of(validToken));

        assertDoesNotThrow(() -> passwordResetService.validateToken("valid-token-value"));
    }

    @Test
    void validateToken_ShouldThrow_WhenTokenNotFound() {
        when(tokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.validateToken("invalid-token"));
        assertEquals("Invalid or expired token", ex.getMessage());
    }

    @Test
    void validateToken_ShouldThrow_WhenTokenIsUsed() {
        validToken.setUsed(true);
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(validToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.validateToken("used-token"));
        assertEquals("Token has already been used", ex.getMessage());
    }

    @Test
    void validateToken_ShouldThrow_WhenTokenIsExpired() {
        validToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(validToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.validateToken("expired-token"));
        assertEquals("Token has expired", ex.getMessage());
    }

    @Test
    void resetPassword_ShouldUpdatePasswordAndMarkTokenUsed_WhenTokenIsValid() {
        when(tokenRepository.findByToken("valid-token-value")).thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(tokenRepository.save(any(PasswordResetToken.class))).thenReturn(validToken);

        passwordResetService.resetPassword("valid-token-value", "newPassword123");

        verify(passwordEncoder).encode("newPassword123");
        verify(userRepository).save(user);
        assertEquals("encodedNewPassword", user.getPassword());
        assertTrue(validToken.isUsed());
        verify(tokenRepository).save(validToken);
    }

    @Test
    void resetPassword_ShouldThrow_WhenTokenNotFound() {
        when(tokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword("invalid-token", "newPassword123"));
        assertEquals("Invalid or expired token", ex.getMessage());
    }

    @Test
    void resetPassword_ShouldThrow_WhenTokenIsUsed() {
        validToken.setUsed(true);
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(validToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword("used-token", "newPassword123"));
        assertEquals("Token has already been used", ex.getMessage());
    }

    @Test
    void resetPassword_ShouldThrow_WhenTokenIsExpired() {
        validToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(validToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword("expired-token", "newPassword123"));
        assertEquals("Token has expired", ex.getMessage());
    }

    @Test
    void cleanupExpiredTokens_ShouldDeleteExpiredTokens() {
        passwordResetService.cleanupExpiredTokens();

        verify(tokenRepository).deleteAllByExpiresAtBefore(any(LocalDateTime.class));
    }
}
