package com.flux.calendar_service.connects.dto;

public record ConnectResponse(
    String userId,
    boolean google,
    boolean zoom
) { }