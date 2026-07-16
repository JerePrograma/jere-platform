package com.jereplatform.kernel.reliability.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ReliabilityJson {

    private final ObjectMapper objectMapper;

    public ReliabilityJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Value cannot be serialized", invalid);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Persisted JSON cannot be deserialized", invalid);
        }
    }
}
