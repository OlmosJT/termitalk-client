package io.olmosjt.client.ui;

import io.olmosjt.client.model.Command;
import io.olmosjt.client.model.CommandType;
import io.olmosjt.client.model.Message;
import io.olmosjt.client.model.MessageType;
import io.olmosjt.client.net.MessageListener;
import io.olmosjt.client.net.NetworkService;
import io.olmosjt.client.ui.state.ClientState;
import io.olmosjt.client.ui.state.UIState;
import io.olmosjt.client.util.LoggerUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The Controller in the Model-View-Controller (MVC) pattern.
 * This class contains the application's core logic. It responds to user input
 * from the View and network events from the NetworkService.
 */
public class ChatClient implements UIController, MessageListener {
  private final NetworkService networkService;
  private UIView view;
  private String pendingRoomId;

  public ClientState getClientState() {
    return clientState;
  }

  private ClientState clientState = ClientState.DISCONNECTED;
  private String username;

  public ChatClient(NetworkService networkService) {
    this.networkService = networkService;
  }

  public void setView(UIView view) {
    this.view = view;
  }

  @Override
  public boolean start(String host, int port) {
    try {
      clientState = ClientState.CONNECTING;
      networkService.setMessageListener(this);
      networkService.connect(host, port);
      return true; // Success
    } catch (IOException e) {
      if (view != null) {
        view.showLoginError("Connection failed: " + e.getMessage());
      }
      clientState = ClientState.DISCONNECTED;
      return false; // Failure
    }
  }

  // --- UIController Implementation (Commands from the View) ---

  @Override
  public void attemptLogin(String username) {
    if (clientState == ClientState.AWAITING_LOGIN) {
      this.username = username;
      networkService.sendRequest(new Command(CommandType.LOGIN, username));
    }
  }

  @Override
  public void createRoom(String roomName) {
    networkService.sendRequest(new Command(CommandType.CREATE_ROOM, roomName));
    requestRoomList();
  }

  @Override
  public void requestRoomList() {
    networkService.sendRequest(new Command(CommandType.LIST_ROOMS, ""));
  }

  @Override
  public void joinRoom(String roomInfo) {
    String roomId = roomInfo.replaceAll("[^0-9]", "");

    if (roomId.isEmpty()) {
      if (view != null) {
        view.addMessage(new Message(MessageType.NOK, "CLIENT", "", "Invalid Room ID format.", Instant.now()));
      }
      return;
    }

    this.pendingRoomId = "#" + roomId;
    networkService.sendRequest(new Command(CommandType.JOIN, roomId));
  }

  @Override
  public void leaveRoom() {
    networkService.sendRequest(new Command(CommandType.LEAVE, ""));
  }

  @Override
  public void sendMessage(String text) {
    networkService.sendRequest(new Command(CommandType.MSG, text));
  }

  @Override
  public void shutdown() {
    networkService.disconnect();
    clientState = ClientState.DISCONNECTED;
  }


  // --- MessageListener Implementation (Events from the Network) ---

  @Override
  public void onMessage(Message message) {
    if (view == null) return;


    if (message.type() == MessageType.SYSTEM && "SOCKET_DISCONNECT".equals(message.sender())) {
      clientState = ClientState.DISCONNECTED;
      view.showLoginError(message.content());
      return;
    }

    // The core logic: process network messages and command the UI accordingly.
    switch (message.type()) {
      case OK:
        handleOkResponse(message);
        break;
      case NOK:
        handleNokResponse(message);
        break;
      case SYSTEM:
      case USER:
      case PRIVATE:
        view.addMessage(message);
        break;
    }
  }

  private void handleOkResponse(Message message) {
    String content = message.content();
    switch (clientState) {
      case CONNECTING:
        // First message from server after connecting
        if (content.startsWith("Welcome!")) {
          clientState = ClientState.AWAITING_LOGIN;
          view.addMessage(message); // Show welcome message on login screen
        }
        break;

      case AWAITING_LOGIN:
        // Response to our LOGIN command
        if (content.startsWith("Welcome,")) {
          clientState = ClientState.AUTHENTICATED;
          view.showState(UIState.LOBBY); // Transition to Lobby screen
        }
        break;

      case AUTHENTICATED:
        LoggerUtil.info("Received OK response in AUTHENTICATED state: " + content);
        // Check for specific OK messages while in Lobby or Room
        if (content.startsWith("Available rooms:")) {
          // This is a response to LIST_ROOMS. Parse and update the UI.
          List<String> rooms = Arrays.stream(content.replace("Available rooms: ", "").split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .collect(Collectors.toList());
          view.updateRoomList(rooms);
        } else if (content.startsWith("Joined room:")) {
          Pattern pattern = Pattern.compile("'([^']*)'");
          Matcher matcher = pattern.matcher(content);
          String roomName = matcher.find() ? matcher.group(1) : "Unknown";

          view.setRoomDetails(roomName, pendingRoomId);

          view.showState(UIState.IN_ROOM);

          pendingRoomId = null;
        } else if (content.equals("You have left the room.")) {
          view.showState(UIState.LOBBY);
        } else {
          view.addMessage(message);
        }
        break;
    }
  }

  private void handleNokResponse(Message message) {
    if (clientState == ClientState.AWAITING_LOGIN) {
      // Most likely a failed login attempt
      view.showLoginError(message.content());
    } else {
      // Show other errors as a system message
      view.addMessage(message);
    }
  }
}
