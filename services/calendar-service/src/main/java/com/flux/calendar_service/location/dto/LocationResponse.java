package com.flux.calendar_service.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Location response with full address and coordinates")
public record LocationResponse(
        @Schema(description = "Unique location ID") String id,
        @Schema(description = "Place name") String placeName,
        @Schema(description = "Street address") String streetAddress,
        @Schema(description = "City") String city,
        @Schema(description = "Country") String country,
        @Schema(description = "Building name") String buildingName,
        @Schema(description = "Floor number") Integer floor,
        @Schema(description = "Room") String room,
        @Schema(description = "Latitude") Double latitude,
        @Schema(description = "Longitude") Double longitude,
        @Schema(description = "Google Place ID") String placeId
) {
}
