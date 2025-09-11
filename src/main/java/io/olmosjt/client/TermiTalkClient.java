package io.olmosjt.client;


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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TermiTalkClient {

  private UIState currentState = UIState.LOGIN;
  private final ServerApi server = new MockServer();

  private String username = "";
  private int lobbyScrollPosition = 0;
  private boolean showJoinDialog = false;
  private String channelIdInput = "";

  private boolean showCreateDialog = false;
  private String roomNameInput = "";


  private String currentChannelName = "";
  private String currentChannelId = "";
  private List<String> channelMessages = new ArrayList<>();
  private String chatInput = "";


  public static void main(String[] args) throws IOException {
    TermiTalkClient app = new TermiTalkClient();
    app.run();
  }

  public void run() throws IOException {
    DefaultTerminalFactory factory = new DefaultTerminalFactory();

    try (
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Terminal terminal = factory.createTerminal();
        Screen screen = new TerminalScreen(terminal);
    ) {

      screen.startScreen();
      screen.setCursorPosition(null); // TIP: HIDES CURSOR INITIALLY

      scheduler.scheduleAtFixedRate(() -> {
        try {
          screen.pollInput();
        } catch (IOException e) {
          // Handle exception if needed
        }
      }, 1, 1, TimeUnit.SECONDS);

      // Main application loop
      while (currentState != UIState.QUIT) {
        draw(screen); // Draw the current UI state

        KeyStroke keyStroke = screen.readInput();
        if (keyStroke != null) {
          handleInput(keyStroke, screen.getTerminalSize());
        }
      }

      scheduler.shutdown();
      screen.stopScreen();
    }
  }

  private void handleInput(KeyStroke keyStroke, TerminalSize size) {
    if (showJoinDialog) {
      handleJoinDialogInput(keyStroke);
      return;
    }

    if (showCreateDialog) {
      handleCreateDialogInput(keyStroke);
      return;
    }

    switch (currentState) {
      case LOGIN:
        handleLoginInput(keyStroke);
        break;
      case LOBBY:
        handleLobbyInput(keyStroke, size);
        break;
      case IN_CHANNEL:
        handleChannelInput(keyStroke);
        break;
    }
  }

  private void handleJoinDialogInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Escape) {
      showJoinDialog = false;
    } else if (keyStroke.getKeyType() == KeyType.Enter) {
      Channel channel = server.joinChannel(channelIdInput);
      if (channel != null) {
        currentState = UIState.IN_CHANNEL;
        currentChannelId = channel.id();
        currentChannelName = channel.name();
        channelMessages = new ArrayList<>(channel.initialMessages());
      }
      showJoinDialog = false;
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!channelIdInput.isEmpty()) {
        channelIdInput = channelIdInput.substring(0, channelIdInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character && Character.isDigit(keyStroke.getCharacter())) {
      channelIdInput += keyStroke.getCharacter();
    }
  }

  private void handleChannelInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.F10) {
      currentState = UIState.LOBBY;
      currentChannelId = "";
      currentChannelName = "";
      channelMessages.clear();
      chatInput = "";
    } else if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!chatInput.isEmpty()) {
        String formattedMessage = server.sendMessage(this.username, this.currentChannelId, chatInput);
        channelMessages.add(formattedMessage);
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

  private void handleLoginInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Enter) {
      if (server.login(username)) {
        currentState = UIState.LOBBY;
      } else {
        // You could add feedback here, e.g., a flashing error message
        username = ""; // Clear invalid username
      }
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!username.isEmpty()) {
        username = username.substring(0, username.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      username += keyStroke.getCharacter();
    }
  }

  private void handleLobbyInput(KeyStroke keyStroke, TerminalSize size) {
    if (keyStroke.getKeyType() == KeyType.F10) {
      currentState = UIState.QUIT;
      return;
    } else if (keyStroke.getKeyType() == KeyType.F2) {
      showJoinDialog = true;
      channelIdInput = "";
      return;
    }  else if (keyStroke.getKeyType() == KeyType.F3) {
      showCreateDialog = true;
      roomNameInput = "";
      return;
    }

    int contentTopY = 4;
    int contentBottomY = size.getRows() - 4;
    int listTopY = contentTopY + 4;
    int listHeight = contentBottomY - listTopY + 1;

    if (listHeight <= 0) return;

    int numRooms = server.listRooms().size();
    int maxScrollPosition = Math.max(0, numRooms - listHeight);

    if (keyStroke.getKeyType() == KeyType.ArrowDown) {
      if (lobbyScrollPosition < maxScrollPosition) {
        lobbyScrollPosition++;
      }
    } else if (keyStroke.getKeyType() == KeyType.ArrowUp) {
      if (lobbyScrollPosition > 0) {
        lobbyScrollPosition--;
      }
    }
  }

  private void handleCreateDialogInput(KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.Escape) {
      showCreateDialog = false;
    } else if (keyStroke.getKeyType() == KeyType.Enter) {
      if (!roomNameInput.isEmpty()) {
        server.createRoom(roomNameInput);
        // The room list will be re-fetched automatically when the lobby is redrawn
      }
      showCreateDialog = false; // Close dialog on Enter
    } else if (keyStroke.getKeyType() == KeyType.Backspace) {
      if (!roomNameInput.isEmpty()) {
        roomNameInput = roomNameInput.substring(0, roomNameInput.length() - 1);
      }
    } else if (keyStroke.getKeyType() == KeyType.Character) {
      // Basic validation to prevent overly long names
      if (roomNameInput.length() < 20) {
        roomNameInput += keyStroke.getCharacter();
      }
    }
  }

  private void draw(Screen screen) throws IOException {
    screen.clear();
    TerminalSize size = screen.getTerminalSize();
    TextGraphics g = screen.newTextGraphics();

    drawMainFrame(g, size);
    drawStatusBar(g, size);

    switch(currentState) {
      case LOGIN:
        drawLoginScreen(g, size);
        break;
      case LOBBY:
        drawLobbyScreen(g, size);
        drawFooterBar(g, size);
        // Draw dialog on top if active
        if (showJoinDialog) {
          drawJoinChannelDialog(g, size);
        }
        if (showCreateDialog) {
          drawCreateRoomDialog(g, size);
        }
        break;
      case IN_CHANNEL:
        drawChannelScreen(g, size);
        break;
    }

    screen.refresh();
  }

  // --- Drawing Methods ---

  private void drawCreateRoomDialog(TextGraphics g, TerminalSize size) {
    int boxWidth = 40;
    int boxHeight = 7;
    int left = (size.getColumns() - boxWidth) / 2;
    int top = (size.getRows() - boxHeight) / 2;

    // Draw the box with a shadow/background fill
    g.fillRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), ' ');
    g.drawRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), '║');
    g.drawLine(left + 1, top, left + boxWidth - 2, top, '═');
    g.drawLine(left + 1, top + boxHeight - 1, left + boxWidth - 2, top + boxHeight - 1, '═');
    g.setCharacter(left, top, '╔');
    g.setCharacter(left + boxWidth - 1, top, '╗');
    g.setCharacter(left, top + boxHeight - 1, '╚');
    g.setCharacter(left + boxWidth - 1, top + boxHeight - 1, '╝');

    String title = "CREATE ROOM";
    g.putString(left + (boxWidth - title.length()) / 2, top, title);

    g.putString(left + 2, top + 2, "Enter Room Name:");
    g.putString(left + 2, top + 3, "> " + roomNameInput);

    // Blinking cursor
    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(left + 4 + roomNameInput.length(), top + 3, '_');
    }
  }

  private void drawJoinChannelDialog(TextGraphics g, TerminalSize size) {
    int boxWidth = 35;
    int boxHeight = 7;
    int left = (size.getColumns() - boxWidth) / 2;
    int top = (size.getRows() - boxHeight) / 2;

    g.fillRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), ' ');
    g.drawRectangle(new TerminalPosition(left, top), new TerminalSize(boxWidth, boxHeight), '║');
    g.drawLine(left + 1, top, left + boxWidth - 2, top, '═');
    g.drawLine(left + 1, top + boxHeight - 1, left + boxWidth - 2, top + boxHeight - 1, '═');
    g.setCharacter(left, top, '╔');
    g.setCharacter(left + boxWidth - 1, top, '╗');
    g.setCharacter(left, top + boxHeight - 1, '╚');
    g.setCharacter(left + boxWidth - 1, top + boxHeight - 1, '╝');

    String title = "JOIN CHANNEL";
    g.putString(left + (boxWidth - title.length()) / 2, top, title);

    g.putString(left + 2, top + 2, "Enter Channel ID #:");
    g.putString(left + 2, top + 3, "> " + channelIdInput);

    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(left + 4 + channelIdInput.length(), top + 3, '_');
    }
  }

  private void drawChannelScreen(TextGraphics g, TerminalSize size) {
    // --- Area Definitions ---
    int innerLeft = 3;
    int contentTopY = 4;
    int contentBottomY = size.getRows() - 4;

    // --- Channel Title ---
    String title = String.format("=[ CHANNEL: %s [#%s] ]=", currentChannelName, currentChannelId);
    g.putString((size.getColumns() - title.length()) / 2, 3, title);

    // --- Message Area ---
    int messageAreaHeight = contentBottomY - contentTopY - 2; // Reserve 2 lines for input box
    int messageTopY = contentTopY;

    // Draw messages from the bottom up
    int messagesToDraw = Math.min(channelMessages.size(), messageAreaHeight);
    for(int i = 0; i < messagesToDraw; i++) {
      int messageIndex = channelMessages.size() - messagesToDraw + i;
      g.putString(innerLeft, messageTopY + i, channelMessages.get(messageIndex));
    }

    // --- Input Box ---
    int inputTopY = contentBottomY - 1;
    g.drawLine(innerLeft - 1, inputTopY, size.getColumns() - innerLeft, inputTopY, '─');
    g.putString(innerLeft - 1, inputTopY + 1, "> " + chatInput);
    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(innerLeft + 1 + chatInput.length(), inputTopY + 1, '_');
    }

    // --- Footer ---
    String footer = "F2: User List | F3: Channel Info | F10: Leave Channel";
    g.putString(2, size.getRows() - 2, footer);
  }

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

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, h:mm:ss a");
    String dateTime = LocalDateTime.now().format(formatter);
    g.putString(2, 1, dateTime);

    String status = "STATUS: [ONLINE]";
    g.putString(size.getColumns() - status.length() - 2, 1, status);
  }

  private void drawFooterBar(TextGraphics g, TerminalSize size) {
    String footer = "F1: Help | F2: Join Channel | F3: Create | F4: PM | F10: Quit";
    g.putString(2, size.getRows() - 2, footer);
  }

  private void drawLoginScreen(TextGraphics g, TerminalSize size) {
    int boxWidth = 40;
    int boxHeight = 7;
    int left = (size.getColumns() - boxWidth) / 2;
    int top = (size.getRows() - boxHeight) / 2;

    TerminalPosition boxTopLeft = new TerminalPosition(left, top);
    TerminalSize boxSize = new TerminalSize(boxWidth, boxHeight);
    g.drawRectangle(boxTopLeft, boxSize, '║');

    g.drawLine(left + 1, top, left + boxWidth - 2, top, '═');
    g.drawLine(left + 1, top + boxHeight - 1, left + boxWidth - 2, top + boxHeight - 1, '═');
    g.setCharacter(left, top, '╔');
    g.setCharacter(left + boxWidth - 1, top, '╗');
    g.setCharacter(left, top + boxHeight - 1, '╚');
    g.setCharacter(left + boxWidth - 1, top + boxHeight - 1, '╝');

    String title = "USER AUTHENTICATION";
    g.putString(left + (boxWidth - title.length()) / 2, top, title);

    // Input prompt
    g.putString(left + 2, top + 2, "Enter Callsign:");
    g.putString(left + 2, top + 3, "> " + username);

    // Blinking cursor
    if (System.currentTimeMillis() % 1000 > 500) {
      g.setCharacter(left + 4 + username.length(), top + 3, '_');
    }

    // Dotted line
    for (int i = 0; i < boxWidth - 4; i++) {
      g.setCharacter(left + 2 + i, top + 5, '░');
    }
  }

  private void drawLobbyScreen(TextGraphics g, TerminalSize size) {
    // --- Area Definitions ---
    int innerLeft = 3;
    int innerRight = size.getColumns() - 4;
    int contentTopY = 4;
    int contentBottomY = size.getRows() - 4;

    // --- Static Content ---
    String title = "=[ LOBBY ]=";
    g.putString((size.getColumns() - title.length()) / 2, 3, title);

    g.putString(innerLeft, contentTopY, "Welcome to the system, " + username + ".");
    g.putString(innerLeft, contentTopY + 2, "Available Channels:");

    // --- Dynamic List and Scrollbar Area ---
    int listTopY = contentTopY + 4;
    int listHeight = contentBottomY - listTopY + 1;
    if (listHeight <= 0) {
      return; // Not enough space to draw the list
    }

    // --- Draw Room List ---
    List<String> rooms = server.listRooms();
    for (int i = 0; i < listHeight; i++) {
      int roomIndex = lobbyScrollPosition + i;
      if (roomIndex < rooms.size()) {
        g.putString(innerLeft, listTopY + i, "- " + rooms.get(roomIndex));
      }
    }

    // --- Draw Scrollbar ---
    int numRooms = rooms.size();
    if (numRooms > listHeight) {
      // Calculate thumb size (proportional to content)
      int thumbHeight = Math.max(1, (listHeight * listHeight) / numRooms);

      // Calculate thumb position
      int scrollableRoomCount = numRooms - listHeight;
      int trackHeightForThumb = listHeight - thumbHeight;
      int thumbTopOffset = 0;
      thumbTopOffset = (int) Math.round(((double) trackHeightForThumb * lobbyScrollPosition) / scrollableRoomCount);

      // Draw the thumb characters within the correct vertical bounds
      for (int i = 0; i < thumbHeight; i++) {
        g.setCharacter(innerRight, listTopY + thumbTopOffset + i, '█');
      }
    }
  }



}
