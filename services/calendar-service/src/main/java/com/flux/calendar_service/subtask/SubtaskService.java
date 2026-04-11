package com.flux.calendar_service.subtask;

import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.subtask.dto.SubtaskRequest;
import com.flux.calendar_service.subtask.dto.SubtaskResponse;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;

@Service
@RequiredArgsConstructor
public class SubtaskService {
    private final SubtaskRepository subtaskRepository;
    private final EventRepository eventRepository;
    private final SubtaskMapper subtaskMapper;

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public String createSubtask(String eventId, String subtaskDescription) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        Subtask subtask = Subtask.builder()
                .subtask(subtaskDescription)
                .isDone(false)
                .event(event)
                .build();

        return subtaskRepository.save(subtask).getId();
    }

    public List<SubtaskResponse> getAllSubtasksByEventId(String eventId) {
        return subtaskRepository.findAllByEventId(eventId).stream()
                .map(subtaskMapper::toSubtaskResponse)
                .collect(Collectors.toList());
    }

    public List<SubtaskResponse> getAllSubtasks() {
        return subtaskRepository.findAll().stream()
                .map(subtaskMapper::toSubtaskResponse)
                .collect(Collectors.toList());
    }

    public SubtaskResponse getById(String id) {
        return subtaskRepository.findById(id)
                .map(subtaskMapper::toSubtaskResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found with ID: " + id));
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public void completeSubtask(String id) {
        Subtask subtask = subtaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found with ID: " + id));
        subtask.setDone(true);
        subtaskRepository.save(subtask);
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public void incompleteSubtask(String id) {
        Subtask subtask = subtaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found with ID: " + id));
        subtask.setDone(false);
        subtaskRepository.save(subtask);
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public SubtaskResponse updateSubtask(String id, SubtaskRequest request) {
        Subtask subtask = subtaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found with ID: " + id));

        subtask.setSubtask(request.subtask());
        subtask.setDone(request.isDone());

        return subtaskMapper.toSubtaskResponse(subtaskRepository.save(subtask));
    }

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month"}, allEntries = true)
    public void deleteSubtask(String id) {
        Subtask subtask = subtaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found with ID: " + id));

        // Remove from Event's list to maintain bidirectional consistency
        if (subtask.getEvent() != null) {
            subtask.getEvent().getSubtasks().remove(subtask);
        }

        subtaskRepository.delete(subtask);
    }
}
