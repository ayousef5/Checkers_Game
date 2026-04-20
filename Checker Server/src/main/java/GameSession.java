import java.util.ArrayList; // needed for lists

public class GameSession { // manages one game between two players

    public Server.ClientThread player1; // first player
    public Server.ClientThread player2; // second player
    public CheckersGame game; // the game logic
    public int sessionId; // unique id for this session

    public GameSession(Server.ClientThread player1, Server.ClientThread player2, int sessionId) { // constructor
        this.player1 = player1; // set player 1
        this.player2 = player2; // set player 2
        this.sessionId = sessionId; // set session id
        this.game = new CheckersGame(player1.username, player2.username); // create game, player1 is red
    }

    public void startGame() { // notify both players game is starting
        // send player1 their color (red) and opponent name
        String[] p1Info = {player2.username, "red"}; // {opponentName, myColor}
        player1.sendMessage(new Message(Message.MessageType.game_start, new Object[]{p1Info, game.board})); // send to player1

        // send player2 their color (black) and opponent name
        String[] p2Info = {player1.username, "black"}; // {opponentName, myColor}
        player2.sendMessage(new Message(Message.MessageType.game_start, new Object[]{p2Info, game.board})); // send to player2
    }

    public void handleMove(Move move, Server.ClientThread sender) { // process an incoming move
        String color = sender == player1 ? "red" : "black"; // determine sender's color

        if (!color.equals(game.currentPlayer)) { // not their turn
            sender.sendMessage(new Message(Message.MessageType.invalid_move, "Not your turn")); // reject
            return;
        }

        if (!game.isValidMove(move, color)) { // move is illegal
            sender.sendMessage(new Message(Message.MessageType.invalid_move, "Invalid move")); // reject
            return;
        }

        game.applyMove(move); // apply the move to the board

        if (game.gameOver) { // check if game ended
            broadcastToGame(new Message(Message.MessageType.game_over, game.winner)); // notify both
        } else {
            broadcastToGame(new Message(Message.MessageType.move, game.board)); // send updated board
        }
    }

    public void broadcastToGame(Message msg) { // send message to both players
        player1.sendMessage(msg); // send to player 1
        player2.sendMessage(msg); // send to player 2
    }

    public void endGame() { // clean up session
        player1.currentGame = null; // clear player 1's game
        player2.currentGame = null; // clear player 2's game
    }
}