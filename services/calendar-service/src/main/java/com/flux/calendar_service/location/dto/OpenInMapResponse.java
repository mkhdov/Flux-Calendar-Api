package com.flux.calendar_service.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "External map URLs for opening a location in Google Maps, Yandex Maps, or app")
public record OpenInMapResponse(
        @Schema(description = "Google Maps URL") String googleMaps,
        @Schema(description = "Yandex Maps URL") String yandexMaps,
        @Schema(description = "Deep link or app URL") String app
) {
}
