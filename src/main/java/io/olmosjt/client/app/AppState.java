package io.olmosjt.client.app;

import io.olmosjt.client.model.Room;
import io.olmosjt.client.model.User;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class AppState {
  private User currentUser;
  private Room currentRoom;
  private final List<String> messageHistory = Collections.synchronizedList(new LinkedList<>());

  public Optional<User> getCurrentUser() {
    return Optional.ofNullable(currentUser);
  }

  public void setCurrentUser(User currentUser) {
    this.currentUser = currentUser;
  }

  public Optional<Room> getCurrentRoom() {
    return Optional.ofNullable(currentRoom);
  }

  public void setCurrentRoom(Room currentRoom) {
    this.currentRoom = currentRoom;
  }

  public List<String> getMessageHistory() {
    return messageHistory;
  }

  public void addMessage(String message) {
    this.messageHistory.add(message);
  }

  public void clearMessages() {
    this.messageHistory.clear();
  }

}
