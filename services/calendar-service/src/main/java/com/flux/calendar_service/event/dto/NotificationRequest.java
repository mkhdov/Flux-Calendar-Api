package com.flux.calendar_service.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class NotificationRequest {
    @Schema(description = "Whether notification is enabled", example = "true")
    private boolean enabled;

    @Schema(description = "Type of notification", example = "EMAIL")
    private String type;

    @Schema(description = "Minutes before event to notify", example = "15")
    private int minutesBefore;
}
