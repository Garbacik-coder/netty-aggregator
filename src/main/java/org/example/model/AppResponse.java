package org.example.model;

import com.fasterxml.jackson.databind.JsonNode;

public record AppResponse(
        JsonNode weather,
        JsonNode fact,
        JsonNode ip
) {}