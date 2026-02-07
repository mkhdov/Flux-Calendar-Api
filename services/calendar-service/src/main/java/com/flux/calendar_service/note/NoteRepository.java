package com.flux.calendar_service.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findAllByEventId(String eventId);
    List<Note> findAllByTaskId(String taskId);
}
