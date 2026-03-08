package com.flux.calendar_service.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import com.flux.calendar_service.attachment.AttachmentMapper;
import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarMapper;
import com.flux.calendar_service.conference.ConferenceMapper;
import com.flux.calendar_service.event.dto.EventRequest;
import com.flux.calendar_service.event.dto.EventResponse;
import com.flux.calendar_service.event.dto.EventUpdateRequest;
import com.flux.calendar_service.location.LocationMapper;
import com.flux.calendar_service.member.MemberMapper;
import com.flux.calendar_service.note.NoteMapper;
import com.flux.calendar_service.subtask.SubtaskMapper;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventMapper {
    private final CalendarMapper calendarMapper;
    private final LocationMapper locationMapper;
    private final ConferenceMapper conferenceMapper;
    private final SubtaskMapper subtaskMapper;
    private final AttachmentMapper attachmentMapper;
    private final MemberMapper memberMapper;
    private final NoteMapper noteMapper;

    public EventResponse toEventResponse(Event event) {
        if (event == null) {
            return null;
        }

        return new EventResponse(
                event.getId(),
                calendarMapper.toCalendarResponse(event.getCalendar()),
                event.getTitle(),
                event.getDescription(),
                event.getColorHex(),
                locationMapper.toLocationResponse(event.getLocation()),
                conferenceMapper.toConferenceResponse(event.getConference()),
                event.getType(),
                event.getStartTime(),
                event.getEndTime(),
                event.isAllDay(),
                event.getSyncStatus(),
                event.getStatus(),
                event.getSubtasks() != null
                        ? event.getSubtasks().stream().map(subtaskMapper::toSubtaskResponse).toList()
                        : Collections.emptyList(),

                event.getAttachments() != null
                        ? event.getAttachments().stream().map(attachmentMapper::toAttachmentResponse).toList()
                        : Collections.emptyList(),

                event.getMembers() != null
                        ? event.getMembers().stream().map(memberMapper::toMemberResponse).toList()
                        : Collections.emptyList(),
                event.getNotes() != null
                        ? event.getNotes().stream().map(noteMapper::toNoteResponse).toList()
                        : Collections.emptyList(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    public Event toEvent(EventRequest request, Calendar calendar) {
        if (request == null) {
            return null;
        }

        Event event = Event.builder()
                .calendar(calendar)
                .title(request.title())
                .description(request.description() != null ? request.description() : "")
                .colorHex(request.colorHex())
                .type(request.type())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .allDay(request.allDay())
                .status(request.status())
                .syncStatus(SyncStatus.PENDING)
                .build();

        event.setSubtasks(request.subtasks() != null
                ? request.subtasks().stream().map(t -> subtaskMapper.toSubtask(t, event)).collect(Collectors.toSet())
                : new HashSet<>());
        event.setAttachments(request.attachments() != null
                ? request.attachments().stream().map(a -> attachmentMapper.toAttachment(a, event))
                        .collect(Collectors.toSet())
                : new HashSet<>());

        return event;
    }

    public void updateEventFromRequest(Event event, EventUpdateRequest request) {
        if (request == null) {
            return;
        }

        if (request.title() != null && !request.title().isBlank()) {
            event.setTitle(request.title());
        }
        if (request.description() != null && !request.description().isBlank()) {
            event.setDescription(request.description());
        }
        if (request.colorHex() != null && !request.colorHex().isBlank()) {
            event.setColorHex(request.colorHex());
        }
        if (request.type() != null) {
            event.setType(request.type());
        }
        if (request.startTime() != null) {
            event.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            event.setEndTime(request.endTime());
        }
        if (request.allDay() != null) {
            event.setAllDay(request.allDay());
        }
        if (request.status() != null) {
            event.setStatus(request.status());
        }
        if (request.attachments() != null) {
            event.getAttachments().clear();
            event.getAttachments().addAll(request.attachments().stream()
                    .map(a -> attachmentMapper.toAttachment(a, event))
                    .toList());
        }
    }
}
