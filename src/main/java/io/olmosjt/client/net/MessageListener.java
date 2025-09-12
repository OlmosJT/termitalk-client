package io.olmosjt.client.net;

import io.olmosjt.client.model.Message;

@FunctionalInterface
public interface MessageListener {
  void onMessage(Message message);
}
