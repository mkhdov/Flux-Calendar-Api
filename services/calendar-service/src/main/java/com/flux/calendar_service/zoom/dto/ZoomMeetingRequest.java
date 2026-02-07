package com.flux.calendar_service.zoom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomMeetingRequest {
    private String topic;
    private Integer type; // 1=instant, 2=scheduled, 3=recurring no fixed time, 8=recurring with fixed
                          // time
    private String startTime; // ISO 8601 format
    private Integer duration; // in minutes
    private String timezone;
    private String agenda;
    private Map<String, Object> settings;
}
