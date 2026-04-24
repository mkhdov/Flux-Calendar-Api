package com.flux.calendar_service.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlannerRequest(
        @JsonProperty("monday") boolean monday,
        @JsonProperty("tuesday") boolean tuesday,
        @JsonProperty("wednesday") boolean wednesday,
        @JsonProperty("thursday") boolean thursday,
        @JsonProperty("friday") boolean friday,
        @JsonProperty("saturday") boolean saturday,
        @JsonProperty("sunday") boolean sunday,
        @JsonProperty("numOfWeeks") int numOfWeeks
) {
}
