package com.flux.calendar_service.note;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.note.dto.NoteRequest;
import com.flux.calendar_service.note.dto.NoteResponse;
import com.flux.calendar_service.task.Task;
import com.flux.calendar_service.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final NoteRepository noteRepository;
    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final NoteMapper noteMapper;

    @Transactional
    public NoteResponse createNoteForEvent(String eventId, NoteRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        Note note = Note.builder()
                .content(request.content())
                .event(event)
                .task(null)
                .build();
        return noteMapper.toNoteResponse(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse createNoteForTask(String taskId, NoteRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + taskId));

        Note note = Note.builder()
                .content(request.content())
                .event(null)
                .task(task)
                .build();
        return noteMapper.toNoteResponse(noteRepository.save(note));
    }

    public List<NoteResponse> getNotesByEventId(String eventId) {
        return noteRepository.findAllByEventId(eventId).stream()
                .map(noteMapper::toNoteResponse)
                .toList();
    }

    public List<NoteResponse> getNotesByTaskId(String taskId) {
        return noteRepository.findAllByTaskId(taskId).stream()
                .map(noteMapper::toNoteResponse)
                .toList();
    }

    public NoteResponse getById(String id) {
        return noteRepository.findById(id)
                .map(noteMapper::toNoteResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with ID: " + id));
    }

    @Transactional
    public NoteResponse updateNote(String id, NoteRequest request) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with ID: " + id));
        note.setContent(request.content());
        return noteMapper.toNoteResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(String id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with ID: " + id));
        if (note.getEvent() != null) {
            note.getEvent().getNotes().remove(note);
        }
        if (note.getTask() != null) {
            note.getTask().getNotes().remove(note);
        }
        noteRepository.delete(note);
    }
}
