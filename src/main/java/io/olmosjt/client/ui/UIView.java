package io.olmosjt.client.ui;

import io.olmosjt.client.model.Message;
import io.olmosjt.client.ui.state.UIState;

import java.util.List;

public interface UIView {
  void setController(UIController controller);
  void start();
  void showState(UIState state);
  void updateRoomList(List<String> rooms);
  void addMessage(Message message);
  void showLoginError(String reason);
  void setRoomDetails(String channelName, String channelId);
  void drawInitialConnectionError();

  /**
   * Displays a transient feedback/status message in the UI.
   * @param text The feedback text
   * @param isError If true, draw it in error style
   */
  void showFeedback(String text, boolean isError);
}
