package io.olmosjt.client.model;

import io.olmosjt.client.util.LoggerUtil;

public enum CommandType {
  LOGIN,
  NICK,
  LIST_ROOMS,
  CREATE_ROOM,
  JOIN,
  LEAVE,
  WHO,
  MSG,
  PRIVMSG,
  QUIT,
  HELP,
  UNKNOWN;

  public static CommandType fromString(String raw) {
    if (raw == null || raw.isBlank()) return UNKNOWN;
    try {
      return CommandType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      LoggerUtil.warn("Couldn't identify command: " + raw + ". Using UNKNOWN");
      LoggerUtil.error(e.getMessage());
      return UNKNOWN;
    }
  }

}
