package io.olmosjt.client;

import io.olmosjt.client.app.AppState;
import io.olmosjt.client.app.ServerConfig;
import io.olmosjt.client.net.ServerConnection;
import io.olmosjt.client.ui.UIManager;

import java.io.IOException;

public class TermiTalkClient {
  public static void main(String[] args) {
    ServerConfig config = ServerConfig.defaultConfig();
    AppState appState = new AppState();

    try {
      // 1. Initialize networking
      ServerConnection connection = new ServerConnection(config.host(), config.port());

      // 2. Initialize UI Manager
      UIManager uiManager = new UIManager(appState, connection);

      // 3. Start the server listener. When a message comes in, add it to the state.
      connection.startListening(appState::addMessage);

      // 5. Start the main UI loop
      uiManager.start();

    } catch (IOException e) {
      System.err.println("Fatal error: " + e.getMessage());
    }
  }
}
