# Checkers Game

A networked multiplayer Checkers game built in Java, featuring a client-server architecture with a graphical user interface for both players and the server.

## Features

- Real-time multiplayer over a network (client-server model)
- Graphical user interface for players (`GuiClient`) and server monitor (`GuiServer`)
- Full checkers rules: piece movement, captures, and king promotion
- Move validation enforced on both client and server
- Session management — the server handles multiple concurrent game sessions

## Tech Stack

- **Language**: Java
- **Build Tool**: Maven
- **IDE**: Eclipse
- **Architecture**: Client-Server (TCP sockets)

## Project Structure

```
Checkers_Game/
├── Checker Client/
│   └── src/main/java/
│       ├── Board.java          # Board state representation
│       ├── CheckersGame.java   # Client-side game logic
│       ├── Client.java         # Network client (socket communication)
│       ├── GuiClient.java      # Player GUI
│       ├── Message.java        # Shared message protocol
│       ├── Move.java           # Move representation
│       └── Piece.java          # Game piece definition
│
└── Checker Server/
    └── src/main/java/
        ├── Board.java          # Board state representation
        ├── CheckersGame.java   # Server-side game logic
        ├── GameSession.java    # Manages a game between two clients
        ├── GuiServer.java      # Server monitor GUI
        ├── Message.java        # Shared message protocol
        ├── Move.java           # Move representation
        ├── Piece.java          # Game piece definition
        └── Server.java         # Main server — accepts connections
```

## Getting Started

### Prerequisites

- Java 8+
- Maven 3.x
- Eclipse IDE (recommended) or any Java IDE

### Build

Build both modules separately using Maven:

```bash
# Build the server
cd "Checker Server"
mvn clean package

# Build the client
cd "Checker Client"
mvn clean package
```

### Run

**Start the server first:**

```bash
cd "Checker Server"
java -jar target/checker-server.jar
```

**Then connect two clients:**

```bash
cd "Checker Client"
java -jar target/checker-client.jar
```

Launch two client instances and connect them to the server's IP address and port to start a game.

## How to Play

1. Start the server — the `GuiServer` window will open showing connection status.
2. Two players each launch the client and connect to the server.
3. Once both players are connected, the game begins automatically.
4. Players alternate turns; click a piece to select it, then click a valid square to move.
5. Captures are made by jumping over an opponent's piece.
6. Reach the opposite end of the board to promote a piece to a King.
## Watch Live Demo at https://drive.google.com/drive/u/2/folders/1LfOCK_aynnBS4fJ7WmVQVhLvIRrJcRb2
