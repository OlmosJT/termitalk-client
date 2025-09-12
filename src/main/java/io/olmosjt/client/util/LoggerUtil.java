package io.olmosjt.client.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggerUtil {
  public enum Level {
    INFO,
    WARN,
    ERROR,
    DEBUG,
    TRACE
  }

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static void log(Level level, String message) {
    String timestamp = LocalDateTime.now().format(FORMATTER);
    System.out.printf("[%s] [%s] %s%n", timestamp, level, message);
  }

  public static void info(String message) {
    log(Level.INFO, message);
  }

  public static void warn(String message) {
    log(Level.WARN, message);
  }

  public static void error(String message) {
    log(Level.ERROR, message);
  }

  public static void debug(String message) {
    log(Level.DEBUG, message);
  }

}
