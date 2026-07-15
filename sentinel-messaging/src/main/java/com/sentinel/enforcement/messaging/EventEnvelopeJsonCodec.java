package com.sentinel.enforcement.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.messaging.EventEnvelope;

public final class EventEnvelopeJsonCodec {
  private final ObjectMapper objectMapper;

  public EventEnvelopeJsonCodec() {
    this.objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public String serialize(EventEnvelope eventEnvelope) {
    try {
      return objectMapper.writeValueAsString(eventEnvelope);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize event envelope.", exception);
    }
  }

  public EventEnvelope deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, EventEnvelope.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize event envelope.", exception);
    }
  }
}
