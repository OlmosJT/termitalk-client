package io.olmosjt.client.model;

public enum MessageType {
  OK,     // successful response to a client request
  NOK,    // error response to a client request
  SYSTEM, // server/system notifications (events)
  USER,   // normal chat messages in rooms
  PRIVATE,// private messages between users
  COMMAND // commands sent from client to server (we won't receive this type)
}

