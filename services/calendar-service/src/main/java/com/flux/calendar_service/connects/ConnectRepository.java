package com.flux.calendar_service.connects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectRepository extends JpaRepository<Connect, String> {
    Optional<Connect> findByUserId(String userId);
}