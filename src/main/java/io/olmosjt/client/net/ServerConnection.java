package io.olmosjt.client.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {
  private final Socket socket;
  private final PrintWriter out;
  private final BufferedReader in;

  public ServerConnection(String host, int port) throws IOException {
    this.socket = new Socket(host, port);
    this.out = new PrintWriter(socket.getOutputStream(), true);
    this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
  }

  /**
   * Starts a background virtual thread to listen for server messages
   */
  public void startListening(Consumer<String> onMessageReceived) {
    Thread.startVirtualThread(() -> {
      try {
        String serverMessage;
        while ((serverMessage = in.readLine()) != null) {
          onMessageReceived.accept(serverMessage);
        }
      } catch (IOException e) {
        onMessageReceived.accept("--- Connection to server lost ---");
      }
    });
  }

  public void sendMessage(String message) {
    out.println(message);
  }

  public void close() throws IOException {
    socket.close();
  }

}
