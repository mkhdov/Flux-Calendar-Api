package com.flux.calendar_service.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {
    List<Task> findAllByCalendarId(String calendarId);

    List<Task> findAllByCalendarIdAndDate(String calendarId, LocalDate date);

    List<Task> findAllByCalendarIdAndStatus(String calendarId, TaskStatus status);
}
