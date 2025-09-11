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
import io.olmosjt.client.app.AppState;
import io.olmosjt.client.model.Room;
import io.olmosjt.client.model.User;
import io.olmosjt.client.net.ServerConnection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UIManager {
  // A new state for when a dialog box is active
  private enum UIState { LOGIN, LOBBY, IN_CHAT, SHOWING_DIALOG, QUITTING }

  // --- State variables for the dialog ---
  private UIState stateBeforeDialog;
  private String dialogPrompt = "";
  private final StringBuilder dialogInputBuffer = new StringBuilder();
  private CompletableFuture<String> dialogFuture;

  private final Screen screen;
  private final AppState appState;
  private final ServerConnection serverConnection;
  private UIState currentState = UIState.LOGIN;
  private final StringBuilder chatInputBuffer = new StringBuilder();

  public UIManager(AppState appState, ServerConnection serverConnection) throws IOException {
    this.appState = appState;
    this.serverConnection = serverConnection;
    Terminal terminal = new DefaultTerminalFactory().createTerminal();
    this.screen = new TerminalScreen(terminal);
  }

  public void start() throws IOException {
    screen.startScreen();
    mainLoop();
    screen.stopScreen();
  }

  private void mainLoop() throws IOException {
    while (currentState != UIState.QUITTING) {
      redraw();
      handleInput();
      try {
        Thread.sleep(15);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void handleInput() {
    KeyStroke keyStroke = null;
    try {
      keyStroke = screen.pollInput();
    } catch (IOException e) {
      appState.addMessage("Error reading input: " + e.getMessage());
    }
    if (keyStroke == null) return;

    switch (currentState) {
      case LOGIN:
        handleLogin();
        break;
      case LOBBY:
        handleLobbyInput(keyStroke);
        break;
      case IN_CHAT:
        handleChatInput(keyStroke);
        break;
      case SHOWING_DIALOG:
        handleDialogInput(keyStroke);
        break;
    }
  }

  // --- Input Handling Methods ---

  private void handleLogin() {
    showInputDialog("Enter your callsign:").thenAccept(name -> {
      if (name != null && !name.isBlank()) {
        appState.setCurrentUser(new User(0, name));
        serverConnection.sendMessage("LOGIN:" + name);
        currentState = UIState.LOBBY;
      } else {
        currentState = UIState.QUITTING;
      }
    });
  }

  private void handleLobbyInput(KeyStroke key) {
    if (key.getKeyType() != KeyType.F10 && key.getKeyType() != KeyType.F2 && key.getKeyType() != KeyType.F3) return;

    switch (key.getKeyType()) {
      case F2: // Join
        showInputDialog("Enter Channel ID #:").thenAccept(idStr -> {
          if (idStr != null) {
            serverConnection.sendMessage("JOIN:" + idStr);
            try {
              appState.setCurrentRoom(new Room(Integer.parseInt(idStr), "Unknown Room"));
              currentState = UIState.IN_CHAT;
            } catch (NumberFormatException e) {
              appState.addMessage("Invalid ID format.");
            }
          }
        });
        break;
      case F3: // Create
        showInputDialog("Enter New Channel Name:").thenAccept(name -> {
          if (name != null) serverConnection.sendMessage("CREATE_ROOM:" + name);
        });
        break;
      case F10: // Quit
        serverConnection.sendMessage("QUIT");
        currentState = UIState.QUITTING;
        break;
    }
  }

  private void handleChatInput(KeyStroke key) {
    switch (key.getKeyType()) {
      case Character:
        chatInputBuffer.append(key.getCharacter());
        break;
      case Backspace:
        if (!chatInputBuffer.isEmpty()) {
          chatInputBuffer.deleteCharAt(chatInputBuffer.length() - 1);
        }
        break;
      case Enter:
        if (!chatInputBuffer.toString().isBlank()) {
          serverConnection.sendMessage("MSG:" + chatInputBuffer);
          chatInputBuffer.setLength(0);
        }
        break;
      case F10: // Leave
        serverConnection.sendMessage("LEAVE");
        appState.setCurrentRoom(null);
        currentState = UIState.LOBBY;
        break;
    }
  }

  private void handleDialogInput(KeyStroke key) {
    switch (key.getKeyType()) {
      case Character:
        dialogInputBuffer.append(key.getCharacter());
        break;
      case Backspace:
        if (!dialogInputBuffer.isEmpty()) {
          dialogInputBuffer.deleteCharAt(dialogInputBuffer.length() - 1);
        }
        break;
      case Enter:
        dialogFuture.complete(dialogInputBuffer.toString());
        currentState = stateBeforeDialog;
        break;
      case Escape:
        dialogFuture.complete(null);
        currentState = stateBeforeDialog;
        break;
    }
  }


  // --- Redrawing Logic ---

  private void redraw() throws IOException {
    TextGraphics tg = screen.newTextGraphics();
    TerminalSize size = screen.getTerminalSize();
    screen.clear();

    drawHeader(tg, size);
    drawMainWindow(tg, size);
    drawFooter(tg, size);

    // If in a dialog state, draw the dialog on top of everything else
    if (currentState == UIState.SHOWING_DIALOG) {
      drawDialog(tg, size);
    }

    screen.refresh();
  }

  private void drawHeader(TextGraphics tg, TerminalSize size) {
    String title = "[ TERMITALK ]";
    String status = "STATUS: [ONLINE]";
    String time = java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMMM yyyy, h:mm:ss a")
            .withZone(java.time.ZoneId.of("Asia/Tashkent"))
            .format(java.time.Instant.now());

    tg.setBackgroundColor(TextColor.ANSI.BLUE);
    tg.fillRectangle(new TerminalPosition(0, 0), new TerminalSize(size.getColumns(), 1), ' ');

    tg.setForegroundColor(TextColor.ANSI.WHITE);
    tg.putString(1, 0, title);
    tg.putString(size.getColumns() - status.length() - 1, 0, status);

    tg.setBackgroundColor(TextColor.ANSI.BLACK);
    tg.setForegroundColor(TextColor.ANSI.CYAN);
    tg.putString(1, 1, time);
  }

  private void drawMainWindow(TextGraphics tg, TerminalSize size) {
    int width = size.getColumns() - 2;
    int height = size.getRows() - 4;
    TerminalPosition position = new TerminalPosition(1, 2);

    tg.setBackgroundColor(TextColor.ANSI.BLACK);
    tg.setForegroundColor(TextColor.ANSI.WHITE);

    // Manually draw the fancy border
    tg.setCharacter(position, '╔');
    tg.setCharacter(position.withRelativeColumn(width - 1), '╗');
    tg.setCharacter(position.withRelativeRow(height - 1), '╚');
    tg.setCharacter(position.withRelativeColumn(width - 1).withRelativeRow(height - 1), '╝');
    tg.drawLine(position.withRelativeColumn(1), position.withRelativeColumn(width - 2), '═');
    tg.drawLine(position.withRelativeRow(height - 1).withRelativeColumn(1), position.withRelativeRow(height - 1).withRelativeColumn(width - 2), '═');
    tg.drawLine(position.withRelativeRow(1), position.withRelativeRow(height - 2), '║');
    tg.drawLine(position.withRelativeColumn(width - 1).withRelativeRow(1), position.withRelativeColumn(width - 1).withRelativeRow(height - 2), '║');

    String title = (currentState == UIState.LOBBY || (currentState == UIState.SHOWING_DIALOG && stateBeforeDialog == UIState.LOBBY))
            ? "LOBBY"
            : "CHANNEL: " + appState.getCurrentRoom().map(Room::name).orElse("???");
    tg.putString(position.getColumn() + 2, position.getRow(), "[ " + title + " ]");

    // Parse and draw content
    List<String> history = appState.getMessageHistory();
    int historySize = history.size();
    int contentHeight = height - 2;
    int maxLines = contentHeight - (currentState == UIState.IN_CHAT ? 2 : 1);
    int startY = position.getRow() + maxLines;

    for (int i = 0; i < maxLines && i < historySize; i++) {
      int msgIndex = historySize - 1 - i;
      String rawMessage = history.get(msgIndex);
      int currentY = startY - i;

      tg.setForegroundColor(TextColor.ANSI.WHITE); // Default color

      String[] parts = rawMessage.split("\\|", 4);
      if (parts.length == 4) {
        String type = parts[0];
        String sender = parts[1];
        String content = parts[3];

        switch (type) {
          case "SYSTEM":
            tg.setForegroundColor(TextColor.ANSI.CYAN);
            tg.putString(position.getColumn() + 2, currentY, "[SYSTEM] " + content);
            break;
          case "ERROR":
            tg.setForegroundColor(TextColor.ANSI.RED);
            tg.putString(position.getColumn() + 2, currentY, "[ERROR] " + content);
            break;
          case "USER":
            tg.setForegroundColor(TextColor.ANSI.YELLOW);
            tg.putString(position.getColumn() + 2, currentY, "<" + sender + "> ");
            tg.setForegroundColor(TextColor.ANSI.WHITE);
            tg.putString(position.getColumn() + 2 + sender.length() + 3, currentY, content);
            break;
          case "PRIVATE":
            tg.setForegroundColor(TextColor.ANSI.GREEN);
            tg.putString(position.getColumn() + 2, currentY, "[PRIVATE from " + sender + "]: " + content);
            break;
          default:
            tg.putString(position.getColumn() + 2, currentY, content);
            break;
        }
      } else {
        tg.putString(position.getColumn() + 2, currentY, rawMessage);
      }
    }

    if (currentState == UIState.IN_CHAT) {
      tg.putString(position.getColumn(), position.getRow() + height - 2, "╟" + "─".repeat(width - 2) + "╢");
      String prompt = "> " + chatInputBuffer.toString();
      tg.putString(position.getColumn() + 2, position.getRow() + height - 1, prompt);
      screen.setCursorPosition(new TerminalPosition(position.getColumn() + 2 + prompt.length(), position.getRow() + height - 1));
    }
  }

  private void drawFooter(TextGraphics tg, TerminalSize size) {
    String hotkeys = "";
    if (currentState == UIState.LOBBY || (currentState == UIState.SHOWING_DIALOG && stateBeforeDialog == UIState.LOBBY)) {
      hotkeys = "F2: Join ┆ F3: Create ┆ F10: Quit";
    } else if (currentState == UIState.IN_CHAT) {
      hotkeys = "F10: Leave Channel";
    }

    tg.setBackgroundColor(TextColor.ANSI.BLUE);
    tg.fillRectangle(new TerminalPosition(0, size.getRows() - 1), new TerminalSize(size.getColumns(), 1), ' ');

    tg.setForegroundColor(TextColor.ANSI.WHITE);
    String userStatus = "User: " + appState.getCurrentUser().map(User::name).orElse("???");
    tg.putString(1, size.getRows() - 1, userStatus);
    tg.putString(size.getColumns() - hotkeys.length() - 1, size.getRows() - 1, hotkeys);
  }

  private void drawDialog(TextGraphics tg, TerminalSize size) {
    int width = Math.max(40, dialogPrompt.length() + 4);
    int height = 5;
    int left = (size.getColumns() - width) / 2;
    int top = (size.getRows() - height) / 2;
    TerminalPosition position = new TerminalPosition(left, top);

    tg.setBackgroundColor(TextColor.ANSI.BLUE);
    tg.fillRectangle(position, new TerminalSize(width, height), ' ');
    tg.setForegroundColor(TextColor.ANSI.WHITE);

    tg.setCharacter(position, '╔');
    tg.setCharacter(position.withRelativeColumn(width - 1), '╗');
    tg.setCharacter(position.withRelativeRow(height - 1), '╚');
    tg.setCharacter(position.withRelativeColumn(width - 1).withRelativeRow(height - 1), '╝');
    tg.drawLine(position.withRelativeColumn(1), position.withRelativeColumn(width - 2), '═');
    tg.drawLine(position.withRelativeRow(height - 1).withRelativeColumn(1), position.withRelativeRow(height - 1).withRelativeColumn(width - 2), '═');
    tg.drawLine(position.withRelativeRow(1), position.withRelativeRow(height - 2), '║');
    tg.drawLine(position.withRelativeColumn(width - 1).withRelativeRow(1), position.withRelativeColumn(width - 1).withRelativeRow(height - 2), '║');

    tg.putString(left + 2, top + 1, dialogPrompt);
    String prompt = "> " + dialogInputBuffer.toString();
    tg.putString(left + 2, top + 3, prompt);
    screen.setCursorPosition(new TerminalPosition(left + 2 + prompt.length(), top + 3));
  }

  private CompletableFuture<String> showInputDialog(String prompt) {
    stateBeforeDialog = currentState;
    currentState = UIState.SHOWING_DIALOG;
    dialogPrompt = prompt;
    dialogInputBuffer.setLength(0);
    dialogFuture = new CompletableFuture<>();
    return dialogFuture;
  }
}