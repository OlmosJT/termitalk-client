package io.olmosjt.client.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import io.olmosjt.client.model.Message;
import io.olmosjt.client.net.NetworkService;
import io.olmosjt.client.net.SocketNetworkService;
import io.olmosjt.client.ui.state.ClientState;
import io.olmosjt.client.ui.state.UIState;
import io.olmosjt.client.util.LoggerUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The View in the Model-View-Controller (MVC) pattern.
 * This class is responsible ONLY for drawing the UI and capturing user input.
 * It is a "dumb" component that delegates all logic to the UIController.
 */
public class TermiTalkClient implements UIView {

  private UIState currentState = UIState.LOGIN;
  private UIController controller;

  // --- UI State Fields (for drawing) ---
  private String usernameInput = "";
  private String loginError = null;
  private long loginErrorTime = 0;

  private List<String> roomList = new ArrayList<>();
  private int lobbyScrollPosition = 0;

  private boolean showJoinDialog = false;
  private String channelIdInput = "";
  private boolean showCreateDialog = false;
  private String roomNameInput = "";

  private String currentChannelName = "";
  private String currentChannelId = "";
  private final List<String> channelMessages = new ArrayList<>();
  private String chatInput = "";

  // Transient feedback/status area
  private String feedbackText = null;
  private boolean feedbackIsError = false;
  private long feedbackAt = 0L;

  public static void main(String[] args) throws IOException {
    NetworkService networkService = new SocketNetworkService();
    ChatClient controller = new ChatClient(networkService);
    UIView ui = new TermiTalkClient();

    ui.setController(controller);
    controller.setView(ui);

    boolean connected = controller.start("127.0.0.1", 9000);

    if (connected) {
      ui.start();
    } else {
      ui.drawInitialConnectionError();
    }
  }

  @Override
  public void setController(UIController controller) {
    this.controller = controller;
  }

  @Override
  public void start() {
    DefaultTerminalFactory factory = new DefaultTerminalFactory();
    try (Terminal terminal = factory.createTerminal();
         Screen screen = new TerminalScreen(terminal)) {

      screen.startScreen();
      screen.setCursorPosition(null);

      TerminalSize lastKnownSize = screen.getTerminalSize();

      while (currentState != UIState.QUIT) {
        TerminalSize newSize = screen.doResizeIfNecessary();
        if (newSize != null) {
          screen.clear();
        }

        draw(screen); // Draw the current UI state

        // Poll for input without blocking for too long
        KeyStroke keyStroke = screen.pollInput();
        if (keyStroke != null) {
          handleInput(keyStroke, screen.getTerminalSize());
        }

        // Add a small sleep to prevent the loop from consuming 100% CPU
        try {
          Thread.sleep(16); // ~60 FPS
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      screen.stopScreen();
    } catch (IOException e) {
      LoggerUtil.error(e.getMessage());
    }
  }

  // --- UIView Implementation (Commands from the Controller) ---

  @Override
  public void showState(UIState state) {
    this.currentState = state;
    // Reset screen-specific data when changing states
    if (state == UIState.LOBBY) {
      channelMessages.clear();
      chatInput = "";
      controller.requestRoomList(); // Ask controller to refresh room list
    } else if (state == UIState.LOGIN) {
      usernameInput = "";
    }
  }

  @Override
  public void updateRoomList(List<String> rooms) {
    this.roomList = rooms;
    // Adjust scroll position if it's out of bounds
    int maxScroll = Math.max(0, rooms.size() - 10); // Assuming a view height of 10
    if(lobbyScrollPosition > maxScroll) {
      lobbyScrollPosition = maxScroll;
    }
  }

  @Override
  public void addMessage(Message message) {
    String formatted = formatServerMessage(message);
    synchronized (channelMessages) {
      channelMessages.add(formatted);
      if (channelMessages.size() > 1000) { // Keep buffer size reasonable
        channelMessages.removeFirst();
      }
    }
  }

  @Override
  public void showLoginError(String reason) {
    this.loginError = "ERROR: " + reason;
    this.loginErrorTime = System.currentTimeMillis();
    this.usernameInput = ""; // Clear the invalid username
  }

  @Override
  public void setRoomDetails(String channelName, String channelId) {
    this.currentChannelName = channelName;
    this.currentChannelId = channelId;
    this.channelMessages.clear(); // Clear old messages
  }

  @Override
  public void showFeedback(String text, boolean isError) {
    this.feedbackText = text;
    this.feedbackIsError = isError;
    this.feedbackAt = System.currentTimeMillis();
  }

  // This method can be reused from the original draft
  private String formatServerMessage(Message m) {
    return switch (m.type()) {
      case USER -> "<" + m.sender() + "> " + m.content();
      case PRIVATE -> "[PM from " + m.sender() + "] " + m.content();
      case SYSTEM -> "[SYSTEM] " + m.content();
      case OK -> "✅ " + m.content();
      case NOK -> "❌ " + m.content();
      default -> m.content();
    };
  }

  // --- Input Handling (Reports user actions to Controller) ---

  private void handleInput(KeyStroke keyStroke, TerminalSize size) {
    if (keyStroke.getKeyType() == KeyType.Escape) {
      showJoinDialog = false;
      showCreateDialog = false;
      return;
    }

    if (showJoinDialog) {
      handleJoinDialogInput(keyStroke);
      return;
    }

    if (showCreateDialog) {
      handleCreateDialogInput(keyStroke);
      return;
    }

    switch (currentState) {
      case LOGIN -> handleLoginInput(keyStroke);
      case LOBBY -> handleLobbyInput(keyStroke, size);
      case IN_ROOM -> handleChannelInput(keyStroke);
      case QUIT -> {} // No input handled in QUIT state
    }
  }

  private void handleLoginInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!usernameInput.isEmpty()) {
        controller.attemptLogin(usernameInput);
      }
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!usernameInput.isEmpty()) {
        usernameInput = usernameInput.substring(0, usernameInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      usernameInput += keyStroke.getCharacter();
    }
  }

  private void handleLobbyInput(KeyStroke keyStroke, TerminalSize size) {
    if (keyStroke.getKeyType() == KeyType.F10) {
      controller.shutdown();
      currentState = UIState.QUIT;
    } else if (keyStroke.getKeyType() == KeyType.F2) {
      showJoinDialog = true;
      channelIdInput = "";
    } else if (keyStroke.getKeyType() == KeyType.F3) {
      showCreateDialog = true;
      roomNameInput = "";
    } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
      if (lobbyScrollPosition < Math.max(0, roomList.size() - 1)) {
        lobbyScrollPosition++;
      }
    } else if (keyStroke.getKeyType() == KeyType.ArrowUp) {
      if (lobbyScrollPosition > 0) {
        lobbyScrollPosition--;
      }
    }
  }

  private void handleJoinDialogInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!channelIdInput.isEmpty()) {
        controller.joinRoom(channelIdInput);
      }
      showJoinDialog = false;
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!channelIdInput.isEmpty()) {
        channelIdInput = channelIdInput.substring(0, channelIdInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      channelIdInput += keyStroke.getCharacter();
    }
  }

  private void handleCreateDialogInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!roomNameInput.isEmpty()) {
        controller.createRoom(roomNameInput);
      }
      showCreateDialog = false;
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!roomNameInput.isEmpty()) {
        roomNameInput = roomNameInput.substring(0, roomNameInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      if (roomNameInput.length() < 30) {
        roomNameInput += keyStroke.getCharacter();
      }
    }
  }

  private void handleChannelInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.F10) {
      controller.leaveRoom();
    } else if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!chatInput.isEmpty()) {
        controller.sendMessage(chatInput);
        chatInput = "";
      }
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!chatInput.isEmpty()) {
        chatInput = chatInput.substring(0, chatInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      chatInput += keyStroke.getCharacter();
    }
  }

  // --- All Drawing Methods (largely unchanged from your draft) ---

  private void draw(Screen screen) throws IOException {
    screen.clear();
    TerminalSize size = screen.getTerminalSize();
    TextGraphics g = screen.newTextGraphics();

    drawMainFrame(g, size);
    drawStatusBar(g, size);
    drawFeedbackBar(g, size);

    switch (currentState) {
      case LOGIN -> drawLoginScreen(g, size);
      case LOBBY -> {
        drawLobbyScreen(g, size);
        drawFooterBar(g, size);
        if (showJoinDialog) drawJoinChannelDialog(g, size);
        if (showCreateDialog) drawCreateRoomDialog(g, size);
      }
      case IN_ROOM -> drawChannelScreen(g, size);
      case QUIT -> {
        String msg = "Disconnecting... Thank you for using TermiTalk!";
        g.putString((size.getColumns() - msg.length()) / 2, size.getRows() / 2, msg);
      }
    }
    screen.refresh();
  }

  @Override
  public void drawInitialConnectionError() {
    DefaultTerminalFactory factory = new DefaultTerminalFactory();
    try (Terminal terminal = factory.createTerminal();
         Screen screen = new TerminalScreen(terminal)) {
      screen.startScreen();
      draw(screen); // This will draw the login screen with the error
      screen.readInput(); // Wait for any key press before exiting
      screen.stopScreen();
    } catch (IOException e) {
      LoggerUtil.error(e.getMessage());
    }
  }

  private void drawDialogBox(TextGraphics g, TerminalSize size, String title, String prompt, String input) {
    int boxWidth = 40;
    int boxHeight = 7;
    int left = (size.getColumns() - boxWidth) / 2;
    int top = (size.getRows() - boxHeight) / 2;

    g.setBackgroundColor(TextColor.ANSI.BLACK);
    g.fillRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), ' ');
    g.drawRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), '║');
    g.drawLine(left + 1, top, left + boxWidth - 2, top, '═');
    g.drawLine(left + 1, top + boxHeight - 1, left + boxWidth - 2, top + boxHeight - 1, '═');
    g.setCharacter(left, top, '╔'); g.setCharacter(left + boxWidth - 1, top, '╗');
    g.setCharacter(left, top + boxHeight - 1, '╚'); g.setCharacter(left + boxWidth - 1, top + boxHeight - 1, '╝');

    g.putString(left + (boxWidth - title.length()) / 2, top, title);
    g.putString(left + 2, top + 2, prompt);
    g.putString(left + 2, top + 3, "> " + input);

    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(left + 4 + input.length(), top + 3, '_');
    }
  }

  private void drawLoginScreen(TextGraphics g, TerminalSize size) {
    // ... (drawing code from your draft, but using `usernameInput`)
    // Centered box drawing logic here...
    String title = "USER AUTHENTICATION";
    int boxWidth = 40;
    int boxHeight = 7;
    int left = (size.getColumns() - boxWidth) / 2;
    int top = (size.getRows() - boxHeight) / 2;
    g.putString(left + 2, top + 2, "Enter Callsign:");
    g.putString(left + 2, top + 3, "> " + usernameInput);
    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(left + 4 + usernameInput.length(), top + 3, '_');
    }

    if (loginError != null) {
      if (!loginError.contains("Connection") && System.currentTimeMillis() - loginErrorTime > 3000) {
        loginError = null;
      } else {
        g.setForegroundColor(TextColor.ANSI.RED);
        g.putString(left + (boxWidth - loginError.length()) / 2, top + 5, loginError);
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
      }
    }
  }

  private void drawLobbyScreen(TextGraphics g, TerminalSize size) {
    // ... (drawing code from your draft, but using `this.roomList`)
    int innerLeft = 3;
    int contentTopY = 4;
    String title = "=[ LOBBY ]=";
    g.putString((size.getColumns() - title.length()) / 2, 3, title);
    g.putString(innerLeft, contentTopY + 2, "Available Channels:");

    int listTopY = contentTopY + 4;
    int listHeight = size.getRows() - 4 - listTopY;

    for (int i = 0; i < listHeight; i++) {
      int roomIndex = lobbyScrollPosition + i;
      if (roomIndex < roomList.size()) {
        g.putString(innerLeft, listTopY + i, "- " + roomList.get(roomIndex));
      }
    }
    // ... (add scrollbar drawing if desired)
  }

  // Include the rest of your excellent drawing methods here:
  // drawMainFrame, drawStatusBar, drawFooterBar, drawChannelScreen,
  // drawJoinChannelDialog, drawCreateRoomDialog, etc. They can be
  // copied almost verbatim from your original draft.
  private void drawMainFrame(TextGraphics g, TerminalSize size) {
    g.setForegroundColor(TextColor.ANSI.WHITE);
    g.drawLine(0, 0, size.getColumns() - 1, 0, '═');
    g.drawLine(0, size.getRows() - 1, size.getColumns() - 1, size.getRows() - 1, '═');
    g.drawLine(0, 2, size.getColumns() - 1, 2, '═');
    g.drawLine(0, size.getRows() - 3, size.getColumns() - 1, size.getRows() - 3, '═');

    g.setCharacter(0, 0, '╔');
    g.setCharacter(size.getColumns() - 1, 0, '╗');
    g.setCharacter(0, 2, '╠');
    g.setCharacter(size.getColumns() - 1, 2, '╣');
    g.setCharacter(0, size.getRows() - 3, '╠');
    g.setCharacter(size.getColumns() - 1, size.getRows() - 3, '╣');
    g.setCharacter(0, size.getRows() - 1, '╚');
    g.setCharacter(size.getColumns() - 1, size.getRows() - 1, '╝');

    for (int y = 1; y < size.getRows() - 1; y++) {
      if (y != 2 && y != size.getRows() - 3) {
        g.setCharacter(0, y, '║');
        g.setCharacter(size.getColumns() - 1, y, '║');
      }
    }
  }

  private void drawStatusBar(TextGraphics g, TerminalSize size) {
    String title = "=[ TERMITALK ]=";
    g.putString((size.getColumns() - title.length()) / 2, 0, title);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    String dateTime = LocalDateTime.now().format(formatter);
    g.putString(2, 1, dateTime);

    String status;

    if (loginError != null && loginError.startsWith("ERROR: Connection")) {
      status = "STATUS: [OFFLINE]";
    } else if (currentState == UIState.QUIT) {
      status = "STATUS: [OFFLINE]";
    } else {
      status = "STATUS: [CONNECTED]";
    }

    g.putString(size.getColumns() - status.length() - 2, 1, status);
  }

  private void drawFeedbackBar(TextGraphics g, TerminalSize size) {
    // Draw a single-line feedback just above the footer divider in Lobby/Login
    // In Room view, draw higher to avoid colliding with the chat input line.
    int y = (currentState == UIState.IN_ROOM) ? (size.getRows() - 6) : (size.getRows() - 4);
    // Auto-clear after 4 seconds (except connection errors)
    if (feedbackText != null && !(loginError != null && loginError.startsWith("ERROR: Connection"))) {
      if (System.currentTimeMillis() - feedbackAt > 4000) {
        feedbackText = null;
      }
    }

    // Clear the line
    String blank = " ".repeat(Math.max(0, size.getColumns() - 4));
    g.putString(2, y, blank);

    if (feedbackText != null && !feedbackText.isBlank()) {
      if (feedbackIsError) {
        g.setForegroundColor(TextColor.ANSI.RED);
      } else {
        g.setForegroundColor(TextColor.ANSI.GREEN);
      }
      String text = feedbackText.length() > size.getColumns() - 4
              ? feedbackText.substring(0, size.getColumns() - 4)
              : feedbackText;
      g.putString(2, y, text);
      g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
  }

  private void drawFooterBar(TextGraphics g, TerminalSize size) {
    String footer = "F2: Join | F3: Create | Arrows: Scroll | F10: Quit";
    g.putString(2, size.getRows() - 2, footer);
  }

  private void drawChannelScreen(TextGraphics g, TerminalSize size) {
    int innerLeft = 3;
    int contentTopY = 4;
    int contentBottomY = size.getRows() - 4;

    String title = String.format("=[ CHANNEL: %s [%s] ]=", currentChannelName, currentChannelId);
    g.putString((size.getColumns() - title.length()) / 2, 3, title);

    int messageAreaHeight = contentBottomY - contentTopY - 2;
    int messageTopY = contentTopY;

    synchronized(channelMessages) {
      int messagesToDraw = Math.min(channelMessages.size(), messageAreaHeight);
      int startIndex = channelMessages.size() - messagesToDraw;
      for(int i = 0; i < messagesToDraw; i++) {
        g.putString(innerLeft, messageTopY + i, channelMessages.get(startIndex + i));
      }
    }

    int inputTopY = contentBottomY - 1;
    g.drawLine(innerLeft - 1, inputTopY, size.getColumns() - innerLeft, inputTopY, '─');
    g.putString(innerLeft - 1, inputTopY + 1, "> " + chatInput);
    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(innerLeft + 1 + chatInput.length(), inputTopY + 1, '_');
    }

    String footer = "F10: Leave Channel";
    g.putString(2, size.getRows() - 2, footer);
  }

  private void drawJoinChannelDialog(TextGraphics g, TerminalSize size) {
    drawDialogBox(g, size, "JOIN CHANNEL", "Enter Room Info (e.g., #123):", channelIdInput);
  }

  private void drawCreateRoomDialog(TextGraphics g, TerminalSize size) {
    drawDialogBox(g, size, "CREATE ROOM", "Enter Room Name:", roomNameInput);
  }
}

