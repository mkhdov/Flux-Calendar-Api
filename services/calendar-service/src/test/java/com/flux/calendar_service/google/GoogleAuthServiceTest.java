package com.flux.calendar_service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.flux.calendar_service.connects.ConnectService;
import com.flux.calendar_service.user.UserSync;
import com.flux.calendar_service.user.UserSyncRepository;
import com.google.api.client.http.HttpTransport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private HttpTransport httpTransport;

    @Mock
    private ConnectService connectService;

    @Mock
    private UserSyncRepository userSyncRepository;

    @Mock
    private GoogleTokenService googleTokenService;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(googleAuthService, "userSyncRepository", userSyncRepository);
        ReflectionTestUtils.setField(googleAuthService, "googleTokenService", googleTokenService);
    }

    @Test
    void markUserAsConnected_ShouldUpdateExistingUserAndConnectGoogle() {
        UserSync userSync = UserSync.builder()
                .id("user-1")
                .googleConnected(false)
                .build();
        when(userSyncRepository.findById("user-1")).thenReturn(Optional.of(userSync));

        googleAuthService.markUserAsConnected("user-1", "user@gmail.com");

        ArgumentCaptor<UserSync> captor = ArgumentCaptor.forClass(UserSync.class);
        verify(userSyncRepository).save(captor.capture());
        UserSync saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("user-1");
        assertThat(saved.isGoogleConnected()).isTrue();
        assertThat(saved.getGoogleDisconnectedAt()).isNull();
        assertThat(saved.getGoogleEmail()).isEqualTo("user@gmail.com");
        verify(connectService).connectGoogle("user-1");
    }

    @Test
    void markUserAsConnected_ShouldCreateUserWhenMissingAndConnectGoogle() {
        when(userSyncRepository.findById("user-2")).thenReturn(Optional.empty());
        when(userSyncRepository.save(any(UserSync.class))).thenAnswer(invocation -> invocation.getArgument(0));

        googleAuthService.markUserAsConnected("user-2", "new-user@gmail.com");

        ArgumentCaptor<UserSync> captor = ArgumentCaptor.forClass(UserSync.class);
        verify(userSyncRepository).save(captor.capture());
        UserSync saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("user-2");
        assertThat(saved.isGoogleConnected()).isTrue();
        assertThat(saved.getGoogleDisconnectedAt()).isNull();
        assertThat(saved.getGoogleEmail()).isEqualTo("new-user@gmail.com");
        verify(connectService).connectGoogle("user-2");
    }
}
