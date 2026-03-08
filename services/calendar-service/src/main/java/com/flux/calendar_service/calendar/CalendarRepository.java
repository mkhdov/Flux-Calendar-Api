package com.flux.calendar_service.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CalendarRepository extends JpaRepository<Calendar, String> {
    List<Calendar> findCalendarByUserId(String userId);
    List<Calendar> findAllByIsDeletedFalse();
    Optional<Calendar> findCalendarByIdAndIsDeletedFalse(String id);
    Optional<Calendar> findCalendarByIdAndIsDeletedTrue(String id);
    List<Calendar> findCalendarByUserIdAndIsDeletedFalse(String userId);
    Optional<Calendar> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
    Optional<Calendar> findByUserIdAndTitleAndIsDeletedFalse(String userId, String title);
    Optional<Calendar> findByUserIdAndTitleAndIsDeletedTrue(String userId, String title);
    Optional<Calendar> findByUserIdAndGoogleCalendarIdAndIsDeletedFalse(String userId, String googleCalendarId);
    Optional<Calendar> findByUserIdAndGoogleCalendarIdAndIsDeletedTrue(String userId, String googleCalendarId);
    Optional<Calendar> findByUserIdAndDescriptionAndIsDeletedFalse(String userId, String description);
    Optional<Calendar> findByUserIdAndDescriptionAndIsDeletedTrue(String userId, String description);
    boolean existsByUserIdAndTitleAndIsDeletedFalse(String userId, String title);

//    @Modifying
//    @Query("UPDATE Calendar c SET c.isPrimary = false WHERE c.userId = :userId AND c.isDeleted = false")
//    int clearPrimaryByUserId(@Param("userId") String userId);
//
//    @Modifying
//    @Query("UPDATE Calendar c SET c.isPrimary = true WHERE c.id = :calendarId AND c.userId = :userId AND c.isDeleted = false")
//    int setPrimaryForUserCalendar(@Param("calendarId") String calendarId, @Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Calendar c SET c.isPrimary = false WHERE c.userId = :userId AND c.isDeleted = false")
    void clearPrimaryByUserId(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Calendar c SET c.isPrimary = true WHERE c.id = :id AND c.userId = :userId AND c.isDeleted = false")
    void setPrimaryForUserCalendar(@Param("id") String id, @Param("userId") String userId);
}
