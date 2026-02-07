package com.flux.calendar_service.note;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.note.dto.NoteRequest;
import com.flux.calendar_service.note.dto.NoteResponse;
import com.flux.calendar_service.task.Task;
import com.flux.calendar_service.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private NoteService noteService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void createNoteForEvent_ShouldReturnNoteResponse() {
        String eventId = "event1";
        NoteRequest request = new NoteRequest("My note content");
        Event event = new Event();
        event.setId(eventId);
        Note note = Note.builder().id("note1").content(request.content()).event(event).build();
        NoteResponse response = new NoteResponse("note1", request.content(), NOW, NOW);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(noteRepository.save(any(Note.class))).thenReturn(note);
        when(noteMapper.toNoteResponse(note)).thenReturn(response);

        NoteResponse result = noteService.createNoteForEvent(eventId, request);

        assertNotNull(result);
        assertEquals("note1", result.id());
        assertEquals("My note content", result.content());
        verify(eventRepository).findById(eventId);
        verify(noteRepository).save(any(Note.class));
    }

    @Test
    void createNoteForEvent_WhenEventNotFound_ShouldThrow() {
        String eventId = "missing";
        NoteRequest request = new NoteRequest("Content");

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.createNoteForEvent(eventId, request));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void createNoteForTask_ShouldReturnNoteResponse() {
        String taskId = "task1";
        NoteRequest request = new NoteRequest("Task note");
        Task task = new Task();
        task.setId(taskId);
        Note note = Note.builder().id("note2").content(request.content()).task(task).build();
        NoteResponse response = new NoteResponse("note2", request.content(), NOW, NOW);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(noteRepository.save(any(Note.class))).thenReturn(note);
        when(noteMapper.toNoteResponse(note)).thenReturn(response);

        NoteResponse result = noteService.createNoteForTask(taskId, request);

        assertNotNull(result);
        assertEquals("note2", result.id());
        assertEquals("Task note", result.content());
        verify(taskRepository).findById(taskId);
        verify(noteRepository).save(any(Note.class));
    }

    @Test
    void createNoteForTask_WhenTaskNotFound_ShouldThrow() {
        String taskId = "missing";
        NoteRequest request = new NoteRequest("Content");

        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.createNoteForTask(taskId, request));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void getNotesByEventId_ShouldReturnList() {
        String eventId = "event1";
        Note note = new Note();
        NoteResponse response = new NoteResponse("1", "content", NOW, NOW);

        when(noteRepository.findAllByEventId(eventId)).thenReturn(List.of(note));
        when(noteMapper.toNoteResponse(note)).thenReturn(response);

        List<NoteResponse> result = noteService.getNotesByEventId(eventId);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("1", result.get(0).id());
    }

    @Test
    void getNotesByTaskId_ShouldReturnList() {
        String taskId = "task1";
        Note note = new Note();
        NoteResponse response = new NoteResponse("2", "task note", NOW, NOW);

        when(noteRepository.findAllByTaskId(taskId)).thenReturn(List.of(note));
        when(noteMapper.toNoteResponse(note)).thenReturn(response);

        List<NoteResponse> result = noteService.getNotesByTaskId(taskId);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("2", result.get(0).id());
    }

    @Test
    void getById_ShouldReturnNote() {
        String id = "note1";
        Note note = new Note();
        NoteResponse response = new NoteResponse(id, "content", NOW, NOW);

        when(noteRepository.findById(id)).thenReturn(Optional.of(note));
        when(noteMapper.toNoteResponse(note)).thenReturn(response);

        NoteResponse result = noteService.getById(id);

        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void getById_WhenNotFound_ShouldThrow() {
        String id = "missing";
        when(noteRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.getById(id));
    }

    @Test
    void updateNote_ShouldReturnUpdatedResponse() {
        String id = "note1";
        NoteRequest request = new NoteRequest("Updated content");
        Note note = new Note();
        note.setId(id);
        Note savedNote = new Note();
        savedNote.setId(id);
        savedNote.setContent(request.content());
        NoteResponse response = new NoteResponse(id, request.content(), NOW, NOW);

        when(noteRepository.findById(id)).thenReturn(Optional.of(note));
        when(noteRepository.save(note)).thenReturn(savedNote);
        when(noteMapper.toNoteResponse(savedNote)).thenReturn(response);

        NoteResponse result = noteService.updateNote(id, request);

        assertEquals("Updated content", result.content());
        verify(noteRepository).save(note);
    }

    @Test
    void updateNote_WhenNotFound_ShouldThrow() {
        String id = "missing";
        NoteRequest request = new NoteRequest("Content");
        when(noteRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.updateNote(id, request));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void deleteNote_ShouldDelete() {
        String id = "note1";
        Note note = new Note();
        note.setId(id);

        when(noteRepository.findById(id)).thenReturn(Optional.of(note));
        doNothing().when(noteRepository).delete(note);

        noteService.deleteNote(id);

        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_WhenNotFound_ShouldThrow() {
        String id = "missing";
        when(noteRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> noteService.deleteNote(id));
        verify(noteRepository, never()).delete(any());
    }
}
