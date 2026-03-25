package com.flux.notification_service.reminder;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flux.notification_service.exception.DuplicateReminderException;
import com.flux.notification_service.exception.ReminderNotFoundException;
import com.flux.notification_service.reminder.dto.ReminderRequest;
import com.flux.notification_service.reminder.dto.ReminderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderMapper reminderMapper;

    @Transactional
    public ReminderResponse createReminder(ReminderRequest request) {
        // Check for duplicates at the application level
        if (reminderRepository.existsByEventIdAndTriggerTimeAndType(
                request.eventId(), request.triggerTime(), request.type())) {
            throw new DuplicateReminderException(
                    "Reminder already exists for event %s at %s with type %s"
                            .formatted(request.eventId(), request.triggerTime(), request.type()));
        }

        Reminder reminder = reminderMapper.toEntity(request);
        Reminder saved = reminderRepository.save(reminder);

        log.info("Created reminder {} for event {} at {} (type={})",
                saved.getId(), saved.getEventId(), saved.getTriggerTime(), saved.getType());

        return reminderMapper.toResponse(saved);
    }

    @Transactional
    public List<ReminderResponse> createReminders(List<ReminderRequest> requests) {
        return requests.stream()
                .map(this::createReminder)
                .collect(Collectors.toList());
    }

    public List<ReminderResponse> getByEventId(String eventId) {
        return reminderRepository.findByEventIdOrderByTriggerTimeAsc(eventId)
                .stream()
                .map(reminderMapper::toResponse)
                .collect(Collectors.toList());
    }

    public ReminderResponse getById(String id) {
        return reminderRepository.findById(id)
                .map(reminderMapper::toResponse)
                .orElseThrow(() -> new ReminderNotFoundException("Reminder not found: " + id));
    }

    @Transactional
    public void deleteReminder(String id) {
        if (!reminderRepository.existsById(id)) {
            throw new ReminderNotFoundException("Reminder not found: " + id);
        }
        reminderRepository.deleteById(id);
        log.info("Deleted reminder {}", id);
    }

    @Transactional
    public void deleteByEventId(String eventId) {
        reminderRepository.deleteByEventId(eventId);
        log.info("Deleted all reminders for event {}", eventId);
    }

    @Transactional
    public void cancelByEventId(String eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdOrderByTriggerTimeAsc(eventId);
        reminders.stream()
                .filter(r -> r.getStatus() == ReminderStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(ReminderStatus.CANCELLED);
                    reminderRepository.save(r);
                });
        log.info("Cancelled all pending reminders for event {}", eventId);
    }
}
