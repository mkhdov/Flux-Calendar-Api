package com.flux.notification_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

@Configuration
@EnableScheduling
@EnableJpaAuditing
@EnableSchedulerLock(defaultLockAtMostFor = "30s")
public class SchedulerConfig {
    // Enables @Scheduled + ShedLock + JPA Auditing (@CreatedDate, @LastModifiedDate)
}
