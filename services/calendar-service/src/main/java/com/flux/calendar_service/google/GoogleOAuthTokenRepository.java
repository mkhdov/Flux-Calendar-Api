package com.flux.calendar_service.google;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GoogleOAuthTokenRepository extends JpaRepository<GoogleOAuthToken, String> {

    Optional<GoogleOAuthToken> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GoogleOAuthToken t where t.userId = :userId")
    Optional<GoogleOAuthToken> findByUserIdForUpdate(@Param("userId") String userId);
}
