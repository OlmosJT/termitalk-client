package io.olmosjt.client.net;

import io.olmosjt.client.model.Command;

import java.io.IOException;

public interface NetworkService {
  void connect(String host, Integer port) throws IOException;
  void disconnect();

  void sendRequest(Command command);

  /**
   * Registers a callback that will be invoked for every message received from the server.
   */
  void setMessageListener(MessageListener listener);

}
