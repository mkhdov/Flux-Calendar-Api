package com.flux.calendar_service.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;

public class FlexibleInstantDeserializer extends StdDeserializer<Instant> {

    public FlexibleInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String rawValue = parser.getText().trim();
            if (rawValue.isBlank()) {
                return null;
            }
            try {
                return FlexibleInstantParser.parse(rawValue);
            } catch (IllegalArgumentException ex) {
                throw InvalidFormatException.from(
                        parser,
                        "Invalid datetime value '" + rawValue + "'. Treated as UTC if timezone is missing.",
                        rawValue,
                        Instant.class);
            }
        }

        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return Instant.ofEpochMilli(parser.getLongValue());
        }

        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }

        return (Instant) context.handleUnexpectedToken(handledType(), parser);
    }
}
