package io.olmosjt.client.net;

import io.olmosjt.client.model.Command;
import io.olmosjt.client.model.Message;
import io.olmosjt.client.model.MessageType;
import io.olmosjt.client.util.LoggerUtil;
import io.olmosjt.client.util.MessageCodec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketNetworkService implements NetworkService {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;
  private volatile boolean running = false;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private MessageListener messageListener;

  @Override
  public void connect(String host, Integer port) throws IOException {
    socket = new Socket(host, port);
    out = new PrintWriter(socket.getOutputStream(), true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    running = true;

    executor.submit(this::listenLoop);
  }

  @Override
  public void sendRequest(Command command) {
    if (out != null && running) {
      String request = "REQ|" + command.type().name() + "|" + command.payload();
      out.println(request);
    }
  }

  @Override
  public void setMessageListener(MessageListener listener) {
    this.messageListener = listener;
  }

  @Override
  public void disconnect() {
    if (!running) return;
    running = false;
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (IOException e) {
      LoggerUtil.error(e.getMessage());
    } finally {
      executor.shutdownNow();
      if (messageListener != null) {
        Message disconnectMsg = new Message(MessageType.SYSTEM, "CLIENT", null, "You have been disconnected.", Instant.now());
        messageListener.onMessage(disconnectMsg);
      }
    }
  }



  private void listenLoop() {
    try {
      String serverLine;
      while (running && (serverLine = in.readLine()) != null) {
        MessageCodec.decode(serverLine).ifPresent(msg -> messageListener.onMessage(msg));
      }
    } catch (IOException e) {
      if (running) {
        Message disconnectMsg = new Message(
                MessageType.SYSTEM, "SOCKET_DISCONNECT", null, "Connection lost to server.", Instant.now()
        );
        if (messageListener != null) {
          messageListener.onMessage(disconnectMsg);
        }
      }
    } finally {
      disconnect();
    }
  }
}
