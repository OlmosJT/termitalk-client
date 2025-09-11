package io.olmosjt.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MockServer implements ServerApi {
  private String username;
  private final List<String> rooms = new ArrayList<>(Arrays.asList(
      "[#100] General Chat",
      "[#101] Gaming Lounge"
  ));
  private int nextRoomId = 102;


  @Override
  public boolean login(String username) {
    if (username != null && username.matches("^[a-zA-Z0-9_]{3,15}$")) {
      this.username = username;
      return true;
    }

    return false;
  }

  @Override
  public List<String> listRooms() {
    return new ArrayList<>(this.rooms);
  }

  @Override
  public Channel joinChannel(String channelId) {
    // Simulate checking if a channel exists
    if ("101".equals(channelId)) {
      List<String> messages = new ArrayList<>(Arrays.asList(
          "[17:10:05] [SYSTEM] 'Bob' has joined Gaming Lounge [#101].",
          "[17:10:11] <Alice> Anyone up for a match?",
          "[17:10:24] <Bob> For sure! Give me 5 mins.",
          "[17:10:58] <Alice> Sounds good!"
      ));
      return new Channel("101", "Gaming Lounge", messages);
    }
    // Return null if channel doesn't exist
    return null;
  }

  @Override
  public String sendMessage(String username, String channelId, String message) {
    // Simulate the server formatting and echoing the message back
    String time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
    return String.format("[%s] <%s> %s", time, username, message);
  }

  @Override
  public boolean createRoom(String roomName) {
    if (roomName == null || !roomName.matches("^[a-zA-Z0-9_-]{3,20}$")) {
      return false;
    }
    // Add the new room to our list
    this.rooms.add(String.format("[#%d] %s", nextRoomId++, roomName));
    return true;
  }
}
