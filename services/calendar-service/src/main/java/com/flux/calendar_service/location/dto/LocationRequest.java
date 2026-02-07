package com.flux.calendar_service.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for creating or updating a location")
public record LocationRequest(
        @Schema(description = "Place name", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @NotBlank String placeName,
        @Schema(description = "Street address") String streetAddress,
        @Schema(description = "City") String city,
        @Schema(description = "Country") String country,
        @Schema(description = "Building name") String buildingName,
        @Schema(description = "Floor number") Integer floor,
        @Schema(description = "Room number or name") String room,
        @Schema(description = "Latitude") Double latitude,
        @Schema(description = "Longitude") Double longitude,
        @Schema(description = "Google Place ID") String placeId) {
}
