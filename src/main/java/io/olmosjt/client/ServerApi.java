package io.olmosjt.client;

import java.util.List;

public interface ServerApi {
  boolean login(String username);
  List<String> listRooms();

  Channel joinChannel(String channelId);
  String sendMessage(String username, String channelId, String message);
  boolean createRoom(String roomName);
}
