package com.flux.calendar_service.location;

import com.flux.calendar_service.location.dto.LocationRequest;
import com.flux.calendar_service.location.dto.LocationResponse;
import com.flux.calendar_service.location.dto.OpenInMapResponse;
import com.flux.calendar_service.location.dto.UpdateLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/event/location")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Endpoints for managing event locations")
public class LocationController {
        private final LocationService locationService;

        @Operation(summary = "Create a location for an event", description = "Creates a new location and associates it with a specific event")
        @ApiResponse(responseCode = "201", description = "Location created successfully")
        @PostMapping("/{id}")
        public ResponseEntity<String> createLocation(
                        @Parameter(description = "ID of the event") @PathVariable("id") String id,
                        @RequestBody @Valid LocationRequest request) {
                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(locationService.addLocation(id, request));
        }

        @Operation(summary = "Get map links for a location", description = "Generates external map links (Google, Yandex) for a specific location")
        @GetMapping("/maps/{id}")
        public ResponseEntity<OpenInMapResponse> openInMaps(
                        @Parameter(description = "ID of the location") @PathVariable String id) {
                return ResponseEntity
                                .status(HttpStatus.OK)
                                .body(locationService.openInMaps(id));
        }

        @Operation(summary = "Get location by ID", description = "Retrieves detailed information about a specific location")
        @ApiResponse(responseCode = "200", description = "Location found")
        @ApiResponse(responseCode = "404", description = "Location not found")
        @GetMapping("/id/{id}")
        public ResponseEntity<LocationResponse> findById(
                        @Parameter(description = "ID of the location") @PathVariable String id) {
                return ResponseEntity
                                .status(HttpStatus.OK)
                                .body(locationService.findById(id));
        }

        @Operation(summary = "Update a location", description = "Updates the details of an existing location")
        @ApiResponse(responseCode = "202", description = "Location update accepted")
        @PutMapping("/{id}")
        public ResponseEntity<?> update(@Parameter(description = "ID of the location") @PathVariable String id,
                        @RequestBody UpdateLocation request) {
                locationService.updateLocation(id, request);
                return ResponseEntity
                                .status(HttpStatus.ACCEPTED)
                                .build();
        }

        @Operation(summary = "Delete a location", description = "Deletes an existing location")
        @ApiResponse(responseCode = "202", description = "Location deletion accepted")
        @DeleteMapping("/{id}")
        public ResponseEntity<?> delete(@Parameter(description = "ID of the location") @PathVariable String id) {
                locationService.deleteLocation(id);
                return ResponseEntity
                                .status(HttpStatus.ACCEPTED)
                                .build();
        }
}
