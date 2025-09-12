package io.olmosjt.client.util;

import io.olmosjt.client.model.Message;
import io.olmosjt.client.model.MessageType;

import java.time.Instant;
import java.util.Optional;

public final class MessageCodec {
  private static final String DELIMITER = "|";
  private static final String DELIMITER_REGEX = "\\|";

  /**
   * Deserializes a raw string from the server into a Message object.
   * Format: TYPE|SENDER|RECIPIENT|PAYLOAD
   *
   * @param rawMessage The pipe-delimited string from the server.
   * @return A structured Message object, or null if the message is malformed.
   */
  public static Optional<Message> decode(String rawMessage) {
    if (rawMessage == null || rawMessage.isBlank()) {
      return null;
    }

    String[] parts = rawMessage.split(DELIMITER_REGEX, 4);
    if (parts.length < 4) {
      return Optional.empty();
    }

    try {
      MessageType type = MessageType.valueOf(parts[0]);
      String sender = parts[1];
      // Recipient can be empty for broadcast messages
      String recipient = parts[2].isEmpty() ? null : parts[2];
      String content = parts[3];

      return Optional.of(new Message(type, sender, recipient, content, Instant.now()));
    } catch (IllegalArgumentException e) {
      // This happens if the MessageType is unknown.
      return Optional.empty();
    }
  }
}
