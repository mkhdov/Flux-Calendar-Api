package com.flux.calendar_service.subtask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, String> {
    List<Subtask> findAllByEventId(String eventId);
    List<Subtask> findAllByEventIdIn(List<String> eventIds);

    List<Subtask> findAllByTaskId(String taskId);
}
