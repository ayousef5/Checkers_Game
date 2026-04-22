import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class GameSession {

    public static final int START_SECONDS = 180;

    public Server.ClientThread player1;
    public Server.ClientThread player2;
    public boolean vsBot;
    public CheckersGame game;
    public int sessionId;
    public ArrayList<Server.ClientThread> spectators = new ArrayList<>();

    private int redSecondsLeft = START_SECONDS;
    private int blackSecondsLeft = START_SECONDS;
    private Timer turnTimer;
    private Timer botMoveTimer;
    private final Server server;
    private boolean finished = false;
    private final Random rng = new Random();

    public GameSession(Server server, Server.ClientThread player1, Server.ClientThread player2, int sessionId) {
        this.server = server;
        this.vsBot = false;
        this.player1 = player1;
        this.player2 = player2;
        this.sessionId = sessionId;
        this.game = new CheckersGame(player1.username, player2.username);
    }

    /** Human (red) vs {@link UserStore#BOT_USERNAME} (black). */
    public GameSession(Server server, Server.ClientThread human, int sessionId) {
        this.server = server;
        this.vsBot = true;
        this.player1 = human;
        this.player2 = null;
        this.sessionId = sessionId;
        this.game = new CheckersGame(human.username, UserStore.BOT_USERNAME);
    }

    public synchronized boolean tryMarkFinished() {
        if (finished) return false;
        finished = true;
        return true;
    }

    public synchronized int getRedSeconds() {
        return redSecondsLeft;
    }

    public synchronized int getBlackSeconds() {
        return blackSecondsLeft;
    }

    public synchronized void startGame() {
        finished = false;
        redSecondsLeft = START_SECONDS;
        blackSecondsLeft = START_SECONDS;
        if (vsBot) {
            int rRed = server.getUserStore().getRating(player1.username);
            int rBlack = UserStore.DEFAULT_RATING;
            int[] times = {redSecondsLeft, blackSecondsLeft};
            String[] p1Info = {UserStore.BOT_USERNAME, "red"};
            player1.sendMessage(new Message(Message.MessageType.game_start,
                    new Object[]{p1Info, game.board, new int[]{rRed, rBlack}, times}));
            startTurnTimer();
            scheduleBotMove();
            return;
        }

        int rRed = server.getUserStore().getRating(game.redPlayer);
        int rBlack = server.getUserStore().getRating(game.blackPlayer);
        int[] times = {redSecondsLeft, blackSecondsLeft};

        String[] p1Info = {player2.username, "red"};
        player1.sendMessage(new Message(Message.MessageType.game_start,
                new Object[]{p1Info, game.board, new int[]{rRed, rBlack}, times}));

        String[] p2Info = {player1.username, "black"};
        player2.sendMessage(new Message(Message.MessageType.game_start,
                new Object[]{p2Info, game.board, new int[]{rRed, rBlack}, times}));

        startTurnTimer();
    }

    public synchronized void addSpectator(Server.ClientThread c) {
        if (c == player1) {
            return;
        }
        if (!vsBot && c == player2) {
            return;
        }
        if (!spectators.contains(c)) {
            spectators.add(c);
        }
    }

    public synchronized void removeSpectator(Server.ClientThread c) {
        spectators.remove(c);
    }

    public synchronized void broadcastToGame(Message msg) {
        player1.sendMessage(msg);
        if (!vsBot) {
            player2.sendMessage(msg);
        }
        for (Server.ClientThread s : new ArrayList<>(spectators)) {
            s.sendMessage(msg);
        }
    }

    public synchronized void broadcastTimer() {
        Message m = new Message(Message.MessageType.timer_sync, new int[]{redSecondsLeft, blackSecondsLeft});
        broadcastToGame(m);
    }

    public synchronized void handleMove(Move move, Server.ClientThread sender) {
        if (sender.isSpectator) {
            return;
        }
        if (vsBot && sender != player1) {
            return;
        }
        String color = (sender == player1) ? "red" : "black";

        if (!color.equals(game.currentPlayer)) {
            sender.sendMessage(new Message(Message.MessageType.invalid_move, "Not your turn"));
            return;
        }

        if (!game.isValidMove(move, color)) {
            sender.sendMessage(new Message(Message.MessageType.invalid_move, "Invalid move"));
            return;
        }

        game.applyMove(move);

        String historyLine = formatMoveHistoryLine(sender.username, move);
        broadcastToGame(new Message(Message.MessageType.move,
                new Object[]{game.board, redSecondsLeft, blackSecondsLeft, historyLine}));

        if (game.gameOver) {
            server.finishGameWithResult(this, game.winner);
        } else {
            broadcastTimer();
            scheduleBotMove();
        }
    }

    private void scheduleBotMove() {
        if (!vsBot) {
            return;
        }
        if (finished || game == null || game.gameOver) {
            return;
        }
        if (!"black".equals(game.currentPlayer)) {
            return;
        }
        cancelBotTimer();
        long delay = 500L + rng.nextInt(500);
        botMoveTimer = new Timer(true);
        final GameSession self = this;
        botMoveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.runBotPly(self);
            }
        }, delay);
    }

    private void cancelBotTimer() {
        if (botMoveTimer != null) {
            botMoveTimer.cancel();
            botMoveTimer = null;
        }
    }

    /** Called on server for bot turn. */
    public synchronized void applyBotMove() {
        if (!vsBot || finished || game.gameOver) {
            return;
        }
        if (!"black".equals(game.currentPlayer)) {
            return;
        }
        if (game.getValidMoves("black").isEmpty()) {
            server.finishGameWithResult(this, game.redPlayer);
            return;
        }
        Move m = CheckersAI.chooseMove(game, "black");
        if (m == null) {
            server.finishGameWithResult(this, game.redPlayer);
            return;
        }
        game.applyMove(m);
        String historyLine = formatMoveHistoryLine(UserStore.BOT_USERNAME, m);
        broadcastToGame(new Message(Message.MessageType.move,
                new Object[]{game.board, redSecondsLeft, blackSecondsLeft, historyLine}));

        if (game.gameOver) {
            server.finishGameWithResult(this, game.winner);
        } else {
            broadcastTimer();
        }
    }

    private void startTurnTimer() {
        stopTurnTimer();
        turnTimer = new Timer(true);
        turnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                onTick();
            }
        }, 1000L, 1000L);
    }

    private void onTick() {
        String timeoutWinner = null;
        synchronized (this) {
            if (finished || game.gameOver) {
                stopTurnTimer();
                return;
            }
            if (game.currentPlayer.equals("red")) {
                redSecondsLeft = Math.max(0, redSecondsLeft - 1);
                if (redSecondsLeft <= 0) {
                    timeoutWinner = game.blackPlayer;
                }
            } else {
                blackSecondsLeft = Math.max(0, blackSecondsLeft - 1);
                if (blackSecondsLeft <= 0) {
                    timeoutWinner = game.redPlayer;
                }
            }
            if (timeoutWinner != null) {
                stopTurnTimer();
            }
        }
        if (timeoutWinner != null) {
            server.finishGameWithResult(this, timeoutWinner);
            return;
        }
        broadcastTimer();
    }

    public synchronized void stopTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    public synchronized void endGameCleanup() {
        stopTurnTimer();
        cancelBotTimer();
        player1.currentGame = null;
        if (player2 != null) {
            player2.currentGame = null;
        }
        player1.isSpectator = false;
        if (player2 != null) {
            player2.isSpectator = false;
        }
        for (Server.ClientThread s : new ArrayList<>(spectators)) {
            s.currentGame = null;
            s.isSpectator = false;
        }
        spectators.clear();
    }

    private static String squareToAlgebraic(int row, int col) {
        return "" + (char) ('A' + col) + (8 - row);
    }

    private static String formatMoveHistoryLine(String username, Move move) {
        String from = squareToAlgebraic(move.fromRow, move.fromCol);
        String to = squareToAlgebraic(move.toRow, move.toCol);
        String cap = (move.capturedPositions != null && !move.capturedPositions.isEmpty()) ? " (x)" : "";
        return "[" + username + "]: " + from + " -> " + to + cap;
    }
}
