package com.flux.calendar_service.subtask;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubtaskServiceTest {

    @Mock
    private SubtaskRepository subtaskRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SubtaskMapper subtaskMapper;

    @InjectMocks
    private SubtaskService subtaskService;

    @Test
    void createSubtask_ShouldReturnId() {
        String eventId = "1";
        String description = "New Subtask";
        Event event = new Event();
        event.setId(eventId);
        Subtask subtask = Subtask.builder().id("sub1").build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(subtaskRepository.save(any(Subtask.class))).thenReturn(subtask);

        String id = subtaskService.createSubtask(eventId, description);

        assertEquals("sub1", id);
    }

    @Test
    void getAllSubtasksByEventId_ShouldReturnList() {
        String eventId = "1";
        Subtask subtask = new Subtask();
        SubtaskResponse response = new SubtaskResponse("1", "desc", false);

        when(subtaskRepository.findAllByEventId(eventId)).thenReturn(List.of(subtask));
        when(subtaskMapper.toSubtaskResponse(subtask)).thenReturn(response);

        List<SubtaskResponse> responses = subtaskService.getAllSubtasksByEventId(eventId);

        assertFalse(responses.isEmpty());
    }

    @Test
    void getById_ShouldReturnSubtask() {
        String id = "1";
        Subtask subtask = new Subtask();
        SubtaskResponse response = new SubtaskResponse("1", "desc", false);

        when(subtaskRepository.findById(id)).thenReturn(Optional.of(subtask));
        when(subtaskMapper.toSubtaskResponse(subtask)).thenReturn(response);

        SubtaskResponse result = subtaskService.getById(id);

        assertNotNull(result);
    }

    @Test
    void completeSubtask_ShouldSetDoneTrue() {
        String id = "1";
        Subtask subtask = new Subtask();
        subtask.setDone(false);

        when(subtaskRepository.findById(id)).thenReturn(Optional.of(subtask));
        when(subtaskRepository.save(subtask)).thenReturn(subtask);

        subtaskService.completeSubtask(id);

        assertTrue(subtask.isDone());
    }

    @Test
    void incompleteSubtask_ShouldSetDoneFalse() {
        String id = "1";
        Subtask subtask = new Subtask();
        subtask.setDone(true);

        when(subtaskRepository.findById(id)).thenReturn(Optional.of(subtask));
        when(subtaskRepository.save(subtask)).thenReturn(subtask);

        subtaskService.incompleteSubtask(id);

        assertFalse(subtask.isDone());
    }

    @Test
    void updateSubtask_ShouldReturnUpdated() {
        String id = "1";
        SubtaskRequest request = new SubtaskRequest("Updated", true);
        Subtask subtask = new Subtask();
        Subtask updatedSubtask = new Subtask();
        SubtaskResponse response = new SubtaskResponse("1", "Updated", true);

        when(subtaskRepository.findById(id)).thenReturn(Optional.of(subtask));
        when(subtaskRepository.save(subtask)).thenReturn(updatedSubtask);
        when(subtaskMapper.toSubtaskResponse(updatedSubtask)).thenReturn(response);

        SubtaskResponse result = subtaskService.updateSubtask(id, request);

        assertEquals("Updated", result.subtask());
        assertTrue(result.isDone());
    }

    @Test
    void deleteSubtask_ShouldDelete() {
        String id = "1";
        Subtask subtask = new Subtask();
        // Event is null here, which is fine for basic delete test

        when(subtaskRepository.findById(id)).thenReturn(Optional.of(subtask));
        doNothing().when(subtaskRepository).delete(subtask);

        subtaskService.deleteSubtask(id);

        verify(subtaskRepository).delete(subtask);
    }
}
