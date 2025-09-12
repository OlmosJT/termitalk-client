package io.olmosjt.client.model;

import java.time.Instant;
import java.util.Objects;

public record Message(
        MessageType type,
        String sender,
        String recipient,
        String content,
        Instant timestamp
) {
  public Message {
    Objects.requireNonNull(type);
    Objects.requireNonNull(sender);
    Objects.requireNonNull(content);
    if (timestamp == null) timestamp = Instant.now();
  }
}
