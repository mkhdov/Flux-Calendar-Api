package com.flux.calendar_service.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Standard error response body returned on validation or server errors")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(description = "Map of field names to error messages (e.g. validation errors)") Map<String, String> errors
) {
}
