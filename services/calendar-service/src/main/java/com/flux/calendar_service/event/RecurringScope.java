package com.flux.calendar_service.event;

/**
 * Defines the scope of an operation on a recurring event series.
 */
public enum RecurringScope {
    /** Only this specific event occurrence */
    THIS_EVENT,
    /** This event and all following events in the series */
    THIS_AND_FOLLOWING,
    /** All events in the recurring series */
    ALL_EVENTS
}
