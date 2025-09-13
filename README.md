### TermiTalk Client

A terminal-based chat client for the TermiTalk server. </br>
This application provides a TUI (text user interface) using Lanterna and communicates with the TermiTalk server over TCP sockets.

Server repository: https://github.com/OlmosJT/termitalk-server

<table>
  <tr>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/99aff6d9-84ca-461f-bc9b-30328e88825e" /> </td>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/a0e1393f-9d85-4726-9c96-c661294bdfb4" /> </td>
  </tr>
  <tr>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/e1c9b2d3-de9e-485b-9343-d5eb8f030b82" /> </td>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/85045dd8-e1e2-419f-b6c7-153bef0aeca3" /> </td>
  </tr>    
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/e3aa144b-ee48-4928-9843-f5a6f0e52d33" /> </td>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/af2f0ac9-ab45-4c9e-a05b-9f2f9a733c5e" /> </td>
  <tr>
    <td> <img width="810" height="548" alt="image" src="https://github.com/user-attachments/assets/a8f0547d-7a48-4679-a915-328502fa2c85" /> </td>
  </tr>
</table>

### Features
- Terminal UI using Lanterna
- Login, lobby, and in-room chat screens
- List rooms, join existing rooms, create new rooms
- Send messages within a room
- Feedback bar for transient status and error messages
- Graceful disconnect handling


### Requirements
- JDK 21 (required by this project)
- Gradle (wrapper can be used)
- A running TermiTalk server instance (see server repo)


### Getting Started
#### 1) Clone the repository
- Client: your local copy (this project)
- Server: https://github.com/OlmosJT/termitalk-server

#### 2) Start the server
- Follow instructions in the server repo to run it.
- By default this client attempts to connect to 127.0.0.1:9000.

#### 3) Build the client
- Using Gradle wrapper:
  - Linux/macOS: `./gradlew clean build`
  - Windows (PowerShell or CMD): `gradlew clean build`

This produces:
- Regular build artifacts under `build/`
- A fat/uber JAR via ShadowJar at `build/libs/termitalk-client-1.0.jar`


### Run the Client
You can run in two ways.

#### Option A: Run from Gradle
- Linux/macOS: `./gradlew run`
- Windows: `gradlew run`

Note: The run task wires standard input to the app, enabling interactive TUI.

#### Option B: Run the fat JAR
- After build, run:
  - `java -jar build/libs/termitalk-client-1.0.jar`


### Default Connection
Currently, the client connects to:
- Host: 127.0.0.1
- Port: 9000

This is hardcoded in `io.olmosjt.client.ui.TermiTalkClient.main`:
- `controller.start("127.0.0.1", 9000);`

To change host/port, adjust this line and rebuild. (Future enhancement could read host/port from args or environment variables.)


### How to Use
1) Launch the app. The login screen appears.
2) Enter a username ("Callsign") and press Enter.
3) On successful login, you’ll enter the lobby, where you can:
   - See available rooms
   - Create a new room
   - Join an existing room
4) After joining a room, type your message and press Enter to send.
5) Use function keys to navigate and exit as described below.


### Keyboard Shortcuts
- Global:
  - Esc: Close any open dialog (Join/Create) and return to previous view

- Login screen:
  - Enter: Attempt login with the typed username
  - Backspace: Delete last character

- Lobby screen:
  - F2: Open "Join channel" dialog (enter a room id reference such as #123 or 123)
  - F3: Open "Create room" dialog (enter room name)
  - Arrow Up/Down: Scroll the room list
  - F10: Quit the application

- In-room (channel) screen:
  - Enter: Send message
  - Backspace: Delete last character in input
  - F10: Leave the current room (returns to lobby)


### UI Overview
- Status bar (top): Shows app title, current time, and connection status
- Main content area: Login form, Lobby room list, or Channel messages + input box
- Feedback bar: Transient status/errors (auto-clears after a few seconds)
- Footer bar:
  - Lobby: "F2: Join | F3: Create | Arrows: Scroll | F10: Quit"
  - Channel: "F10: Leave Channel"


### Server Protocol (Client-Side View)
- Outgoing requests from client: `REQ|<COMMAND>|<PAYLOAD>`
  - Examples:
    - `REQ|LOGIN|alice`
    - `REQ|LIST_ROOMS|`
    - `REQ|CREATE_ROOM|My Room`
    - `REQ|JOIN|123`
    - `REQ|LEAVE|`
    - `REQ|MSG|Hello everyone!`

- Incoming messages decoded as: `TYPE|SENDER|RECIPIENT|PAYLOAD`
  - TYPE is one of: OK, NOK, SYSTEM, USER, PRIVATE
  - The client displays them in the UI and drives state transitions:
    - OK + Welcome -> move to AWAITING_LOGIN
    - OK + Welcome,<user> -> authenticated, show Lobby
    - OK + Available rooms: ... -> update room list
    - OK + Joined room '...' -> switch to in-room view
    - Leave/left-room events -> return to lobby
    - NOK messages -> show as error feedback

Note: The UI currently focuses on room-based messages and server events. Private messaging is decoded and displayed if received, but there is no dedicated UI input to initiate private messages from the client.


### Configuration and Extensibility
- Host/Port: Adjust in `TermiTalkClient.main` and rebuild
- UI: Built with Lanterna 3.1.2; most drawing code is in `TermiTalkClient`
- Network: `SocketNetworkService` manages TCP I/O and background listening using virtual threads
- Controller: `ChatClient` implements the application logic (MVC pattern)


### Building a Release
- The ShadowJar task is configured in `build.gradle`:
  - Archive name: `termitalk-client`
  - Version: `1.0`
  - Output: `build/libs/termitalk-client-1.0.jar`

Distribute the fat JAR so users do not need to resolve dependencies manually.


### Troubleshooting
- App says OFFLINE on status bar:
  - Ensure the server is running and reachable at 127.0.0.1:9000
  - If using a remote server or different port, modify `TermiTalkClient.main`
  - Check firewall rules for TCP port 9000

- Build fails:
  - Verify JDK 21 is installed and selected (Gradle and IDE project SDK)

- TUI rendering issues in terminal:
  - Use a standard terminal emulator that supports ANSI; ensure the locale and font handle box-drawing characters

- High CPU usage:
  - The UI loop uses a short sleep (~16ms) to reduce CPU. If needed, you can increase this sleep duration.


### Project Structure (Key Files)
- `src/main/java/io/olmosjt/client/ui/TermiTalkClient.java` — TUI View
- `src/main/java/io/olmosjt/client/ui/ChatClient.java` — Controller and state transitions
- `src/main/java/io/olmosjt/client/net/SocketNetworkService.java` — TCP networking
- `src/main/java/io/olmosjt/client/util/MessageCodec.java` — Message decoding
- `src/main/java/io/olmosjt/client/model/*` — Message and command types


### Roadmap Ideas
- CLI flags or environment variables for host/port
- Scrollback and paging for chat history
- Select room from list with keyboard and press Enter to join
- Dedicated private messaging UI
- Theming and color customization


### Credits
- Built with Java 21 and Lanterna 3.1.2
- Server: TermiTalk by @OlmosJT
