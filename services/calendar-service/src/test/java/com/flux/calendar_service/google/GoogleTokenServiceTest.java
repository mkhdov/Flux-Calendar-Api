package com.flux.calendar_service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.flux.calendar_service.connects.ConnectService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GoogleTokenServiceTest.TestConfig.class)
@TestPropertySource(properties = {
        "google.calendar.client-id=test-client-id",
        "google.calendar.client-secret=test-client-secret"
})
class GoogleTokenServiceTest {

    @Autowired
    private GoogleTokenService googleTokenService;

    @Autowired
    private GoogleOAuthTokenRepository tokenRepository;

    @Autowired
    private ConnectService connectService;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = GoogleOAuthTokenRepository.class)
    static class TestConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:calendar_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setPackagesToScan("com.flux.calendar_service.google");
            factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            java.util.Properties props = new java.util.Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            factoryBean.setJpaProperties(props);
            return factoryBean;
        }

        @Bean
        public PlatformTransactionManager transactionManager(
                LocalContainerEntityManagerFactoryBean entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory.getObject());
        }

        @Bean
        public HttpTransport googleHttpTransport() {
            return new MockHttpTransport() {
                @Override
                public LowLevelHttpRequest buildRequest(String method, String url) {
                    return new MockLowLevelHttpRequest(url) {
                        @Override
                        public LowLevelHttpResponse execute() {
                            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                            response.setContentType("application/json");
                            response.setContent("{\"access_token\":\"new-access-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
                            return response;
                        }
                    };
                }
            };
        }

        @Bean
        public ConnectService connectService() {
            return mock(ConnectService.class);
        }

        @Bean
        public GoogleTokenService googleTokenService(
                GoogleOAuthTokenRepository tokenRepository,
                HttpTransport httpTransport,
                ConnectService connectService) {
            return new GoogleTokenService(tokenRepository, httpTransport, connectService);
        }
    }

    @Test
    void saveTokensAfterOAuthCallback() {
        TokenResponse response = Mockito.mock(TokenResponse.class);
        when(response.getAccessToken()).thenReturn("access-token");
        when(response.getRefreshToken()).thenReturn("refresh-token");
        when(response.getExpiresInSeconds()).thenReturn(3600L);
        when(response.getTokenType()).thenReturn("Bearer");
        when(response.getScope()).thenReturn("calendar");

        googleTokenService.saveTokenFromResponse("user-1", response);

        GoogleOAuthToken stored = tokenRepository.findById("user-1").orElseThrow();
        assertThat(stored.getAccessToken()).isEqualTo("access-token");
        assertThat(stored.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(stored.getExpiresAt()).isNotNull();
    }

    @Test
    void loadTokensFromDatabase() throws Exception {
        GoogleOAuthToken token = GoogleOAuthToken.builder()
                .userId("user-2")
                .accessToken("db-access-token")
                .refreshToken("db-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .updatedAt(Instant.now())
                .build();
        tokenRepository.save(token);

        Credential credential = googleTokenService.getValidCredential("user-2");

        assertThat(credential).isNotNull();
        assertThat(credential.getAccessToken()).isEqualTo("db-access-token");
    }

    @Test
    void refreshExpiredAccessTokens() throws Exception {
        GoogleOAuthToken token = GoogleOAuthToken.builder()
                .userId("user-3")
                .accessToken("expired-access-token")
                .refreshToken("refresh-token")
                .expiresAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now())
                .build();
        tokenRepository.save(token);

        Credential credential = googleTokenService.getValidCredential("user-3");

        assertThat(credential).isNotNull();
        assertThat(credential.getAccessToken()).isEqualTo("new-access-token");

        GoogleOAuthToken refreshed = tokenRepository.findById("user-3").orElseThrow();
        assertThat(refreshed.getAccessToken()).isEqualTo("new-access-token");
        assertThat(refreshed.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void deleteToken_ShouldDisconnectGoogle() {
        GoogleOAuthToken token = GoogleOAuthToken.builder()
                .userId("user-4")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .updatedAt(Instant.now())
                .build();
        tokenRepository.save(token);

        googleTokenService.deleteToken("user-4");

        assertThat(tokenRepository.findById("user-4")).isEmpty();
        verify(connectService).disconnectGoogle("user-4");
    }
}
