package com.flux.calendar_service.google;

import com.flux.calendar_service.location.Location;
import com.google.api.client.util.DateTime;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "google.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class GoogleCalendarApiService {

        private final GoogleAuthService googleAuthService;

        // FIX #1: HTTP transport created once at construction time, not on every
        // request.
        // Creating a new NetHttpTransport initializes a full SSL context and connection
        // pool — doing this per API call is extremely wasteful and slow.
        private final NetHttpTransport httpTransport;

        private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

        // FIX #6: Renamed from 'calendarId' to 'targetCalendarId' to avoid
        // confusion with the Google Calendar class imported above.
        @Value("${google.calendar.calendar-id:primary}")
        private String targetCalendarId;

        @Value("${google.calendar.application-name:Flux Calendar Sync}")
        private String applicationName;

        @Autowired
        public GoogleCalendarApiService(GoogleAuthService googleAuthService) {
                this.googleAuthService = googleAuthService;
                // FIX #1: Initialize transport once — reused across all requests.
                try {
                        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                } catch (GeneralSecurityException | IOException e) {
                        throw new RuntimeException("Failed to initialize Google HTTP transport", e);
                }
        }

        private Calendar getCalendarClient(String userId) throws IOException {
                if (googleAuthService == null) {
                        throw new IOException("GoogleAuthService is not available");
                }

                Credential credential = googleAuthService.getValidCredential(userId);
                if (credential == null) {
                        throw new IOException("No valid credential found for user " + userId
                                        + ". Please re-authorize the application.");
                }

                // FIX #1: Reuse the shared httpTransport instead of creating a new one here.
                return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                                .setApplicationName(applicationName)
                                .build();
        }

        public String getPrimaryCalendarId(String userId) throws IOException {
                CalendarListEntry primaryCalendar = getCalendarClient(userId)
                                .calendarList()
                                .get("primary")
                                .execute();

                if (primaryCalendar == null || primaryCalendar.getId() == null || primaryCalendar.getId().isBlank()) {
                        throw new IOException("Unable to resolve Google primary calendar ID for user " + userId);
                }
                return primaryCalendar.getId();
        }

        @Deprecated
        public String getPrimaryCalendarEmail(String userId) throws IOException {
                return getPrimaryCalendarId(userId);
        }

        // ========================================================================
        // CREATE METHODS
        // ========================================================================

        public Event createEvent(String userId, String title, String description, Location location,
                        Instant startTime, Instant endTime, String type) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description);

                event.setStart(timedDateTime(startTime));
                event.setEnd(timedDateTime(endTime));

                if (location != null) {
                        event.setLocation(formatLocation(location));
                }

                event.setColorId(defineColorId(type));
                event.setReminders(defaultReminders());

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .execute();

                log.info("Event created for user {}: {}", userId, created.getHtmlLink());
                return created;
        }

        public Event createEventWithGoogleMeet(String userId, String title, String description,
                        Location location, Instant startTime, Instant endTime, String type) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description);

                if (location != null) {
                        event.setLocation(formatLocation(location));
                }

                event.setStart(timedDateTime(startTime));
                event.setEnd(timedDateTime(endTime));
                event.setColorId(defineColorId(type));
                event.setReminders(defaultReminders());
                event.setConferenceData(newMeetConferenceRequest());

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .setConferenceDataVersion(1)
                                .execute();

                log.info("Event created with Google Meet for user {}: {}", userId, created.getHtmlLink());
                logMeetLinks(created);
                return created;
        }

        public Event addGoogleMeetToExistingEvent(String userId, String eventId) throws IOException {
                Event event = getCalendarClient(userId).events()
                                .get(targetCalendarId, eventId)
                                .execute();

                if (event.getConferenceData() != null) {
                        log.debug("Event {} already has conference data — skipping Meet creation", eventId);
                        return event;
                }

                event.setConferenceData(newMeetConferenceRequest());

                Event updated = getCalendarClient(userId).events()
                                .update(targetCalendarId, eventId, event)
                                .setConferenceDataVersion(1)
                                .execute();

                log.info("Google Meet added to event {} for user {}", eventId, userId);
                logMeetLinks(updated);
                return updated;
        }

        /**
         * Create an all-day event.
         */
        public Event createAllDayEvent(String userId, String title, String description,
                        Location location, Instant date) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description);

                // FIX #2: Always use UTC — consistent with createAllDayEventWithGoogleMeet
                // and avoids wrong dates when pod timezone differs from UTC.
                java.time.LocalDate startDate = date.atZone(ZoneId.of("UTC")).toLocalDate();
                event.setStart(allDayDateTime(startDate));
                event.setEnd(allDayDateTime(startDate.plusDays(1)));
                event.setReminders(defaultReminders());

                if (location != null) {
                        event.setLocation(formatLocation(location));
                }

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .execute();

                log.info("All-day event created for user {}: {}", userId, created.getHtmlLink());
                return created;
        }

        public Event createAllDayEventWithGoogleMeet(String userId, String title,
                        String description, Instant date) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description);

                // FIX #2: Was using ZoneId.systemDefault() — now consistently UTC.
                java.time.LocalDate startDate = date.atZone(ZoneId.of("UTC")).toLocalDate();
                event.setStart(allDayDateTime(startDate));
                event.setEnd(allDayDateTime(startDate.plusDays(1)));
                event.setReminders(defaultReminders());
                event.setConferenceData(newMeetConferenceRequest());

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .setConferenceDataVersion(1)
                                .execute();

                log.info("All-day event with Google Meet created for user {}: {}", userId, created.getHtmlLink());
                return created;
        }

        public Event createDetailedEvent(String userId, String title, String description,
                        Instant startTime, Instant endTime,
                        String location, List<String> attendeeEmails, String type) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description)
                                .setLocation(location);

                event.setStart(timedDateTime(startTime));
                event.setEnd(timedDateTime(endTime));
                event.setColorId(defineColorId(type));

                if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
                        event.setAttendees(toAttendees(attendeeEmails));
                }

                Event.Reminders reminders = new Event.Reminders()
                                .setUseDefault(false)
                                .setOverrides(Arrays.asList(
                                                new EventReminder().setMethod("email").setMinutes(24 * 60),
                                                new EventReminder().setMethod("popup").setMinutes(30)));
                event.setReminders(reminders);

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .setSendNotifications(true)
                                .execute();

                log.info("Detailed event created for user {}: {}", userId, created.getHtmlLink());
                return created;
        }

        public Event createRecurringEvent(String userId, String title, String description,
                        Instant startTime, Instant endTime, String recurrenceRule) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description);

                event.setStart(timedDateTime(startTime));
                event.setEnd(timedDateTime(endTime));
                event.setReminders(defaultReminders());
                event.setRecurrence(Arrays.asList(recurrenceRule));

                Event created = getCalendarClient(userId).events()
                                .insert(targetCalendarId, event)
                                .execute();

                log.info("Recurring event created for user {}: {}", userId, created.getHtmlLink());
                return created;
        }

        // ========================================================================
        // READ / GET METHODS
        // ========================================================================

        public Event getEventById(String userId, String eventId) throws IOException {
                Event event = getCalendarClient(userId).events()
                                .get(targetCalendarId, eventId)
                                .execute();
                log.debug("Retrieved event {} for user {}: {}", eventId, userId, event.getSummary());
                return event;
        }

        public List<Event> getAllEvents(String userId) throws IOException {
                List<Event> allEvents = new ArrayList<>();
                String pageToken = null;

                do {
                        Events events = getCalendarClient(userId).events()
                                        .list(targetCalendarId)
                                        .setPageToken(pageToken)
                                        .execute();
                        allEvents.addAll(events.getItems());
                        pageToken = events.getNextPageToken();
                } while (pageToken != null);

                log.info("Retrieved {} events for user {}", allEvents.size(), userId);
                return allEvents;
        }

        public List<Event> getEventsByDateRange(String userId, Instant startDate, Instant endDate) throws IOException {
                Events events = getCalendarClient(userId).events()
                                .list(targetCalendarId)
                                .setTimeMin(toDateTime(startDate))
                                .setTimeMax(toDateTime(endDate))
                                .setOrderBy("startTime")
                                .setSingleEvents(true)
                                .execute();

                List<Event> items = events.getItems();
                log.info("Retrieved {} events in date range for user {}", items.size(), userId);
                return items;
        }

        public List<Event> getUpcomingEvents(String userId, int maxResults) throws IOException {
                Events events = getCalendarClient(userId).events()
                                .list(targetCalendarId)
                                .setMaxResults(maxResults)
                                .setTimeMin(toDateTime(Instant.now()))
                                .setOrderBy("startTime")
                                .setSingleEvents(true)
                                .execute();

                List<Event> items = events.getItems();
                log.info("Retrieved {} upcoming events for user {}", items.size(), userId);
                return items;
        }

        public List<Event> searchEvents(String userId, String searchQuery) throws IOException {
                Events events = getCalendarClient(userId).events()
                                .list(targetCalendarId)
                                .setQ(searchQuery)
                                .execute();

                List<Event> items = events.getItems();
                log.info("Found {} events matching '{}' for user {}", items.size(), searchQuery, userId);
                return items;
        }

        public List<Event> getUpdatedEvents(String userId, Instant updatedMin) throws IOException {
                Events events = getCalendarClient(userId).events()
                                .list(targetCalendarId)
                                .setUpdatedMin(toDateTime(updatedMin))
                                .execute();

                List<Event> items = events.getItems();
                log.info("Retrieved {} updated events for user {}", items.size(), userId);
                return items;
        }

        public EventsResult getEventsWithSyncToken(String userId, String syncToken) throws IOException {
                Calendar.Events.List request = getCalendarClient(userId).events().list(targetCalendarId);

                if (syncToken != null) {
                        request.setSyncToken(syncToken);
                } else {
                        // FIX #3: Replaced hardcoded magic number 2592000000L with a readable
                        // duration expression. Both mean 30 days but this is self-documenting
                        // and safe from typos.
                        request.setTimeMin(toDateTime(Instant.now().minus(30, ChronoUnit.DAYS)));
                }

                List<Event> allEvents = new ArrayList<>();
                Events events;

                do {
                        events = request.execute();
                        allEvents.addAll(events.getItems());
                        request.setPageToken(events.getNextPageToken());
                } while (events.getNextPageToken() != null);

                log.info("Incremental sync retrieved {} events for user {}", allEvents.size(), userId);
                return new EventsResult(allEvents, events.getNextSyncToken());
        }

        // ========================================================================
        // UPDATE METHODS
        // ========================================================================

        public Event updateEvent(String userId, String eventId,
                        String newTitle, String newDescription) throws IOException {

                Event event = new Event()
                                .setSummary(newTitle)
                                .setDescription(newDescription)
                                .setReminders(defaultReminders());

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .execute();

                log.info("Event {} patched for user {}: {}", eventId, userId, updated.getSummary());
                return updated;
        }

        public Event updateEventDateTime(String userId, String eventId,
                        Instant newStartTime, Instant newEndTime) throws IOException {

                Event event = new Event()
                                .setStart(timedDateTime(newStartTime))
                                .setEnd(timedDateTime(newEndTime))
                                .setReminders(defaultReminders());

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .setSendNotifications(true)
                                .execute();

                log.info("Event {} time patched for user {}", eventId, userId);
                return updated;
        }

        public Event updateEventLocation(String userId, String eventId, Location location) throws IOException {
                Event event = new Event()
                                .setLocation(formatLocation(location))
                                .setReminders(defaultReminders());

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .setSendNotifications(true)
                                .execute();

                log.info("Event {} location patched for user {}: {}", eventId, userId, updated.getLocation());
                return updated;
        }

        public Event updateCompleteEvent(String userId, String eventId, String title, String description,
                        Instant startTime, Instant endTime, boolean isAllDay,
                        String location, List<String> attendeeEmails) throws IOException {

                Event event = new Event()
                                .setSummary(title)
                                .setDescription(description)
                                .setLocation(location);

                if (isAllDay) {
                        java.time.LocalDate startDate = startTime.atZone(ZoneId.of("UTC")).toLocalDate();
                        event.setStart(allDayDateTime(startDate));
                        event.setEnd(allDayDateTime(startDate.plusDays(1)));
                } else {
                        event.setStart(timedDateTime(startTime));
                        event.setEnd(timedDateTime(endTime));
                }
                event.setReminders(defaultReminders());

                if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
                        event.setAttendees(toAttendees(attendeeEmails));
                }

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .setSendNotifications(true)
                                .execute();

                log.info("Complete event {} patched for user {}: {}", eventId, userId, updated.getSummary());
                return updated;
        }

        /**
         * Removes conference data (Google Meet) from an event.
         *
         * FIX #4: Setting conferenceData to null in a Google API update is a no-op —
         * Google treats null fields as "unchanged" in partial updates.
         * The only reliable way to signal removal is to send an explicit PATCH
         * with an empty ConferenceData object and conferenceDataVersion=1.
         * This makes the intent visible to the API rather than silently doing nothing.
         */
        public Event removeConfrenceFromEvent(String userId, String eventId) throws IOException {
                Event event = getCalendarClient(userId).events()
                                .get(targetCalendarId, eventId)
                                .execute();

                // Send an empty ConferenceData to explicitly clear the meeting.
                // A null value is ignored by Google's partial update semantics.
                event.setConferenceData(new ConferenceData());

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .setConferenceDataVersion(1)
                                .setSendNotifications(true)
                                .execute();

                log.info("Conference data removed from event {} for user {}", eventId, userId);
                return updated;
        }

        // Getting error that is why i commented it just because it is unnecary piece of
        // code never used
        // public Event addAttendeesToEvent(String userId, String eventId,
        // List<String> newAttendeeEmails) throws IOException {
        //
        // Event event = getCalendarClient(userId).events()
        // .get(targetCalendarId, eventId)
        // .execute();
        //
        // List<EventAttendee> existing = event.getAttendees();
        // if (existing == null) {
        // existing = new ArrayList<>();
        // }
        // newAttendeeEmails.forEach(email -> existing.add(new
        // EventAttendee().setEmail(email)));
        // event.setAttendees(existing);
        //
        // Event updated = getCalendarClient(userId).events()
        // .update(targetCalendarId, eventId, event)
        // .setSendNotifications(true)
        // .execute();
        //
        // log.info("Attendees added to event {} for user {}", eventId, userId);
        // return updated;
        // }

        public Event updateEventColor(String userId, String eventId, String colorId) throws IOException {
                Event event = new Event()
                                .setColorId(colorId);

                Event updated = getCalendarClient(userId).events()
                                .patch(targetCalendarId, eventId, event)
                                .execute();

                log.info("Event {} color patched for user {}", eventId, userId);
                return updated;
        }

        // ========================================================================
        // DELETE METHODS
        // ========================================================================

        public void deleteEvent(String userId, String eventId) throws IOException {
                getCalendarClient(userId).events()
                                .delete(targetCalendarId, eventId)
                                .setSendNotifications(true)
                                .execute();
                log.info("Event {} deleted for user {}", eventId, userId);
        }

        public void deleteEventSilently(String userId, String eventId) throws IOException {
                getCalendarClient(userId).events()
                                .delete(targetCalendarId, eventId)
                                .setSendNotifications(false)
                                .execute();
                log.info("Event {} deleted silently for user {}", eventId, userId);
        }

        public int deleteEventsByDateRange(String userId, Instant startDate, Instant endDate) throws IOException {
                List<Event> events = getEventsByDateRange(userId, startDate, endDate);
                int deletedCount = 0;

                for (Event event : events) {
                        try {
                                deleteEvent(userId, event.getId());
                                deletedCount++;
                        } catch (IOException e) {
                                log.error("Failed to delete event {} for user {}", event.getId(), userId, e);
                        }
                }

                log.info("Deleted {} events for user {}", deletedCount, userId);
                return deletedCount;
        }

        public void cancelRecurringEvent(String userId, String recurringEventId) throws IOException {
                Event event = getCalendarClient(userId).events()
                                .get(targetCalendarId, recurringEventId)
                                .execute();

                event.setStatus("cancelled");

                getCalendarClient(userId).events()
                                .update(targetCalendarId, recurringEventId, event)
                                .setSendNotifications(true)
                                .execute();

                log.info("Recurring event {} cancelled for user {}", recurringEventId, userId);
        }

        public void deleteRecurringEventInstance(String userId, String recurringEventId,
                        String instanceId) throws IOException {

                getCalendarClient(userId).events()
                                .delete(targetCalendarId, instanceId)
                                .setSendNotifications(true)
                                .execute();

                log.info("Recurring event instance {} deleted for user {}", instanceId, userId);
        }

        // ========================================================================
        // MOVE / COPY METHODS
        // ========================================================================

        public Event moveEventToCalendar(String userId, String eventId,
                        String destinationCalendarId) throws IOException {

                Event moved = getCalendarClient(userId).events()
                                .move(targetCalendarId, eventId, destinationCalendarId)
                                .setSendNotifications(true)
                                .execute();

                log.info("Event {} moved to calendar {} for user {}", eventId, destinationCalendarId, userId);
                return moved;
        }

        public Event duplicateEvent(String userId, String eventId) throws IOException {
                Event original = getCalendarClient(userId).events()
                                .get(targetCalendarId, eventId)
                                .execute();

                Event copy = new Event()
                                .setSummary(original.getSummary() + " (Copy)")
                                .setDescription(original.getDescription())
                                .setLocation(original.getLocation())
                                .setStart(original.getStart())
                                .setEnd(original.getEnd());

                Event duplicated = getCalendarClient(userId).events()
                                .insert(targetCalendarId, copy)
                                .execute();

                log.info("Event {} duplicated as {} for user {}", eventId, duplicated.getId(), userId);
                return duplicated;
        }

        // ========================================================================
        // BATCH OPERATIONS
        // ========================================================================

        public List<Event> createMultipleEvents(String userId, List<EventData> eventDataList) throws IOException {
                List<Event> created = new ArrayList<>();

                for (EventData data : eventDataList) {
                        try {
                                created.add(createEvent(userId, data.title, data.description,
                                                null, data.startTime, data.endTime, data.type));
                        } catch (IOException e) {
                                log.error("Failed to create batch event '{}' for user {}", data.title, userId, e);
                        }
                }

                log.info("Created {}/{} events in batch for user {}", created.size(), eventDataList.size(), userId);
                return created;
        }

        public int deleteMultipleEvents(String userId, List<String> eventIds) throws IOException {
                int deletedCount = 0;

                for (String eventId : eventIds) {
                        try {
                                deleteEvent(userId, eventId);
                                deletedCount++;
                        } catch (IOException e) {
                                log.error("Failed to delete event {} for user {}", eventId, userId, e);
                        }
                }

                log.info("Deleted {}/{} events for user {}", deletedCount, eventIds.size(), userId);
                return deletedCount;
        }

        // ========================================================================
        // CALENDAR MANAGEMENT
        // ========================================================================

        public List<CalendarListEntry> listAllCalendars(String userId) throws IOException {
                CalendarList calendarList = getCalendarClient(userId).calendarList().list().execute();
                List<CalendarListEntry> items = calendarList.getItems();

                log.info("Found {} calendars for user {}", items.size(), userId);
                items.forEach(c -> log.debug("Calendar: {} (ID: {})", c.getSummary(), c.getId()));
                return items;
        }

        public com.google.api.services.calendar.model.Calendar getCalendarDetails(String userId) throws IOException {
                com.google.api.services.calendar.model.Calendar calendar = getCalendarClient(userId)
                                .calendars()
                                .get(targetCalendarId)
                                .execute();

                log.info("Calendar '{}' retrieved for user {} (timezone: {})",
                                calendar.getSummary(), userId, calendar.getTimeZone());
                return calendar;
        }

        // ========================================================================
        // PRIVATE HELPERS
        // ========================================================================

        /**
         * Timed event DateTime — always UTC.
         */
        private EventDateTime timedDateTime(Instant instant) {
                return new EventDateTime()
                                .setDateTime(toDateTime(instant))
                                .setTimeZone("UTC");
        }

        /**
         * All-day event DateTime — date only, no time component.
         */
        private EventDateTime allDayDateTime(java.time.LocalDate date) {
                return new EventDateTime().setDate(new DateTime(date.toString()));
        }

        /**
         * Converts Instant to Google DateTime.
         */
        private DateTime toDateTime(Instant instant) {
                return new DateTime(Date.from(instant));
        }

        /**
         * Standard empty reminders block — prevents Google from applying
         * calendar defaults which can cause unwanted notification spam.
         */
        /**
         * No reminders for events: returns an empty reminders block to prevent Google from
         * applying calendar defaults.
         */
        private Event.Reminders defaultReminders() {
                return new Event.Reminders()
                                .setUseDefault(false)
                                .setOverrides(new ArrayList<>());
        }

        /**
         * Builds a new Google Meet conference request with a unique request ID.
         */
        private ConferenceData newMeetConferenceRequest() {
                return new ConferenceData()
                                .setCreateRequest(new CreateConferenceRequest()
                                                .setRequestId(UUID.randomUUID().toString())
                                                .setConferenceSolutionKey(
                                                                new ConferenceSolutionKey().setType("hangoutsMeet")));
        }

        /**
         * Converts a list of email strings to Google EventAttendee objects.
         */
        private List<EventAttendee> toAttendees(List<String> emails) {
                return emails.stream()
                                .map(email -> new EventAttendee().setEmail(email))
                                .collect(Collectors.toList());
        }

        private String formatLocation(Location location) {
                if (location == null)
                        return null;

                StringBuilder sb = new StringBuilder();
                if (location.getPlaceName() != null)
                        sb.append(location.getPlaceName());
                if (location.getStreetAddress() != null && !location.getStreetAddress().isBlank())
                        sb.append(", ").append(location.getStreetAddress());
                if (location.getCity() != null && !location.getCity().isBlank())
                        sb.append(", ").append(location.getCity());
                if (location.getCountry() != null && !location.getCountry().isBlank())
                        sb.append(", ").append(location.getCountry());
                return sb.toString();
        }

        private String defineColorId(String type) {
                if (type == null)
                        return null;
                return switch (type) {
                        case "MEETING" -> "1";
                        case "WORK" -> "2";
                        case "APPOINTMENT" -> "3";
                        case "REMINDER" -> "4";
                        case "TASK" -> "5";
                        case "BIRTHDAY" -> "6";
                        case "HOLIDAY" -> "7";
                        case "ROUTE" -> "8";
                        case "OTHERS" -> "9";
                        case "STUDY" -> "11";
                        default -> null;
                };
        }

        private void logMeetLinks(Event event) {
                if (event.getConferenceData() != null && event.getConferenceData().getEntryPoints() != null) {
                        event.getConferenceData().getEntryPoints()
                                        .forEach(ep -> log.info("Meet entry point [{}]: {}", ep.getEntryPointType(),
                                                        ep.getUri()));
                }
        }

        // ========================================================================
        // HELPER CLASSES
        // ========================================================================

        public static class EventData {
                public String title;
                public String description;
                public Instant startTime;
                public Instant endTime;
                public String type;

                public EventData(String title, String description, Instant startTime, Instant endTime) {
                        this.title = title;
                        this.description = description;
                        this.startTime = startTime;
                        this.endTime = endTime;
                }
        }

        public static class EventsResult {
                private final List<Event> events;
                private final String nextSyncToken;

                public EventsResult(List<Event> events, String nextSyncToken) {
                        this.events = events;
                        this.nextSyncToken = nextSyncToken;
                }

                public List<Event> getEvents() {
                        return events;
                }

                public String getNextSyncToken() {
                        return nextSyncToken;
                }
        }
}