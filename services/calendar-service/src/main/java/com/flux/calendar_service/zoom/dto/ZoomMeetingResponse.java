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
public class ZoomMeetingResponse {
    private Long id;
    private String uuid;
    private String hostId;
    private String hostEmail;
    private String topic;
    private Integer type;
    private String startTime;
    private Integer duration;
    private String timezone;
    private String agenda;
    private String startUrl;
    private String joinUrl;
    private String password;
    private Map<String, Object> settings;
}
