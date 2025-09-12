package io.olmosjt.client.ui;

public interface UIController {

  public boolean start(String host, int port);

  void attemptLogin(String username);
  void requestRoomList();
  void createRoom(String roomName);
  void joinRoom(String roomId);
  void sendMessage(String text);
  void leaveRoom();
  void shutdown();
}
