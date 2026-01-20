package com.flux.calendar_service.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserEvent {
    private String userId;
    private String email;
    private String firstname;
    private String lastname;
    private String type; // e.g., "CREATED", "UPDATED", "DELETED"
}
