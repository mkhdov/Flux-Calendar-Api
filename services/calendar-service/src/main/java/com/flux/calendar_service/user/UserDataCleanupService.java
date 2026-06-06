package com.flux.calendar_service.user;

import com.flux.calendar_service.calendar.Calendar;
import com.flux.calendar_service.calendar.CalendarRepository;
import com.flux.calendar_service.connects.ConnectRepository;
import com.flux.calendar_service.event.Event;
import com.flux.calendar_service.event.EventRepository;
import com.flux.calendar_service.google.GoogleOAuthTokenRepository;
import com.flux.calendar_service.location.Location;
import com.flux.calendar_service.location.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDataCleanupService {

    private final EventRepository eventRepository;
    private final CalendarRepository calendarRepository;
    private final LocationRepository locationRepository;
    private final UserSyncRepository userSyncRepository;
    private final ConnectRepository connectRepository;
    private final GoogleOAuthTokenRepository googleOAuthTokenRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    @CacheEvict(cacheNames = {"events:by-range", "events:month", "events:detail"}, allEntries = true)
    public void cleanupDeletedUser(String userId) {
        List<Event> events = eventRepository.findByCalendar_UserId(userId);
        List<String> eventIds = events.stream()
                .map(Event::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Location> eventLocations = events.stream()
                .map(Event::getLocation)
                .filter(Objects::nonNull)
                .collect(LinkedHashMap<String, Location>::new,
                        (locationsById, location) -> {
                            if (location.getId() != null) {
                                locationsById.putIfAbsent(location.getId(), location);
                            }
                        },
                        Map::putAll)
                .values()
                .stream()
                .toList();
        List<Calendar> calendars = calendarRepository.findCalendarByUserId(userId);

        calendarRepository.deleteAll(calendars);
        calendarRepository.flush();

        deleteOrphanLocations(eventLocations);
        userSyncRepository.deleteById(userId);
        connectRepository.findByUserId(userId).ifPresent(connectRepository::delete);
        googleOAuthTokenRepository.findByUserId(userId).ifPresent(googleOAuthTokenRepository::delete);

        eventIds.forEach(this::sendReminderDeletion);

        log.info("Cleaned deleted user data for user ID: {}. Removed {} calendars and {} events.",
                userId, calendars.size(), eventIds.size());
    }

    private void deleteOrphanLocations(List<Location> locations) {
        for (Location location : locations) {
            if (eventRepository.findEventByLocationId(location.getId()).isEmpty()) {
                locationRepository.delete(location);
            }
        }
    }

    private void sendReminderDeletion(String eventId) {
        try {
            kafkaTemplate.send("reminder-events", eventId, Map.of(
                    "eventId", eventId,
                    "action", "DELETE"));
        } catch (Exception e) {
            log.warn("Failed to send reminder deletion for event {} during user cleanup: {}",
                    eventId, e.getMessage());
        }
    }
}
