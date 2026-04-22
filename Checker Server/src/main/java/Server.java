import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Server {

    int count = 1;
    ArrayList<ClientThread> clients = new ArrayList<>();
    ArrayList<ClientThread> waitingQueue = new ArrayList<>();
    ArrayList<GameSession> activeSessions = new ArrayList<>();
    TheServer server;
    private Consumer<Serializable> callback;
    private final UserStore userStore = new UserStore("users.txt");

    Server(Consumer<Serializable> call) {
        callback = call;
        server = new TheServer();
        server.start();
    }

    public UserStore getUserStore() {
        return userStore;
    }

    public void matchPlayers() {
        if (waitingQueue.size() >= 2) {
            ClientThread player1 = waitingQueue.remove(0);
            ClientThread player2 = waitingQueue.remove(0);
            GameSession session = new GameSession(this, player1, player2, count++);
            activeSessions.add(session);
            player1.currentGame = session;
            player2.currentGame = session;
            player1.lastOpponentName = player2.username;
            player2.lastOpponentName = player1.username;
            session.startGame();
            callback.accept("Game started between " + player1.username + " and " + player2.username);
        }
    }

    public void finishGameWithResult(GameSession session, String outcome) {
        synchronized (session) {
            if (!session.tryMarkFinished()) {
                return;
            }
            session.stopTurnTimer();
        }
        try {
            if ("draw".equals(outcome)) {
                String r = session.game.redPlayer;
                String b = session.game.blackPlayer;
                int rr = userStore.getRating(r);
                int br = userStore.getRating(b);
                int nr = UserStore.computeNewRating(rr, br, 0.5);
                int nb = UserStore.computeNewRating(br, rr, 0.5);
                userStore.updateTwoRatings(r, nr, b, nb);
            } else {
                String w = outcome;
                String r = session.game.redPlayer;
                String b = session.game.blackPlayer;
                String l = w.equals(r) ? b : r;
                int wr = userStore.getRating(w);
                int lr = userStore.getRating(l);
                int nw = UserStore.computeNewRating(wr, lr, 1.0);
                int nl = UserStore.computeNewRating(lr, wr, 0.0);
                userStore.updateTwoRatings(w, nw, l, nl);
            }
        } catch (Exception e) {
            callback.accept("Rating save failed: " + e.getMessage());
        }

        session.broadcastToGame(new Message(Message.MessageType.game_over, outcome));

        int p1r = userStore.getRating(session.player1.username);
        int p2r = userStore.getRating(session.player2.username);
        session.player1.rating = p1r;
        session.player2.rating = p2r;
        session.player1.sendMessage(new Message(Message.MessageType.rating_update, p1r));
        session.player2.sendMessage(new Message(Message.MessageType.rating_update, p2r));

        session.endGameCleanup();
        activeSessions.remove(session);
    }

    public void removeClient(ClientThread client) {
        clients.remove(client);
        waitingQueue.remove(client);
        GameSession g = client.currentGame;
        if (g != null) {
            if (client.isSpectator) {
                g.removeSpectator(client);
                client.currentGame = null;
                client.isSpectator = false;
            } else {
                ClientThread opp = g.player1 == client ? g.player2 : g.player1;
                finishGameWithResult(g, opp.username);
            }
        }
        callback.accept((client.username != null ? client.username : "Client") + " disconnected");
    }

    public class TheServer extends Thread {

        public void run() {
            try (ServerSocket mysocket = new ServerSocket(5555)) {
                System.out.println("Server is waiting for a client!");
                while (true) {
                    ClientThread c = new ClientThread(mysocket.accept(), count);
                    callback.accept("Client #" + count + " connected");
                    clients.add(c);
                    c.start();
                    count++;
                }
            } catch (Exception e) {
                callback.accept("Server socket did not launch");
            }
        }
    }

    class ClientThread extends Thread {

        Socket connection;
        int count;
        java.io.ObjectInputStream in;
        java.io.ObjectOutputStream out;
        String username;
        int rating = 1200;
        GameSession currentGame;
        boolean isSpectator = false;
        String lastOpponentName;

        ClientThread(Socket s, int count) {
            this.connection = s;
            this.count = count;
        }

        public void sendMessage(Message msg) {
            try {
                out.reset();
                out.writeObject(msg);
            } catch (Exception e) {
                callback.accept("Failed to send message to " + username);
            }
        }

        public void run() {
            try {
                out = new java.io.ObjectOutputStream(connection.getOutputStream());
                in = new java.io.ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);
            } catch (Exception e) {
                System.out.println("Streams not open");
            }

            while (true) {
                try {
                    Message msg = (Message) in.readObject();
                    handleMessage(msg);
                } catch (Exception e) {
                    removeClient(this);
                    break;
                }
            }
        }

        private boolean nameTakenOnline(String name) {
            for (ClientThread c : clients) {
                if (c != this && name.equals(c.username)) {
                    return true;
                }
            }
            return false;
        }

        private void handleMessage(Message msg) {
            switch (msg.type) {

                case register:
                    try {
                        String[] creds = (String[]) msg.data;
                        String name = creds[0].trim();
                        String pass = creds[1];
                        if (name.isEmpty() || pass.isEmpty()) {
                            sendMessage(new Message(Message.MessageType.auth_fail, "Username and password required."));
                            return;
                        }
                        if (nameTakenOnline(name)) {
                            sendMessage(new Message(Message.MessageType.auth_fail, "That account is already connected."));
                            return;
                        }
                        synchronized (userStore) {
                            if (userStore.userExists(name)) {
                                sendMessage(new Message(Message.MessageType.auth_fail, "Username already taken."));
                                return;
                            }
                            userStore.registerUser(name, UserStore.sha256Hex(pass));
                        }
                        this.username = name;
                        this.rating = userStore.getRating(name);
                        sendMessage(new Message(Message.MessageType.auth_ok, this.rating));
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.auth_fail, "Could not register."));
                    }
                    break;

                case login:
                    try {
                        String[] creds = (String[]) msg.data;
                        String name = creds[0].trim();
                        String pass = creds[1];
                        if (name.isEmpty() || pass.isEmpty()) {
                            sendMessage(new Message(Message.MessageType.auth_fail, "Username and password required."));
                            return;
                        }
                        if (nameTakenOnline(name)) {
                            sendMessage(new Message(Message.MessageType.auth_fail, "That account is already connected."));
                            return;
                        }
                        String hash = UserStore.sha256Hex(pass);
                        synchronized (userStore) {
                            if (!userStore.verifyLogin(name, hash)) {
                                sendMessage(new Message(Message.MessageType.auth_fail, "Invalid username or password."));
                                return;
                            }
                        }
                        this.username = name;
                        this.rating = userStore.getRating(name);
                        sendMessage(new Message(Message.MessageType.auth_ok, this.rating));
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.auth_fail, "Could not log in."));
                    }
                    break;

                case join_queue:
                    if (username == null || isSpectator) break;
                    waitingQueue.remove(this);
                    waitingQueue.add(this);
                    sendMessage(new Message(Message.MessageType.waiting, null));
                    callback.accept(username + " joined the queue");
                    matchPlayers();
                    break;

                case list_games:
                    ArrayList<GameListEntry> list = new ArrayList<>();
                    for (GameSession gs : activeSessions) {
                        int rr = userStore.getRating(gs.game.redPlayer);
                        int rb = userStore.getRating(gs.game.blackPlayer);
                        list.add(new GameListEntry(gs.sessionId, gs.game.redPlayer, gs.game.blackPlayer, rr, rb));
                    }
                    sendMessage(new Message(Message.MessageType.games_list, list));
                    break;

                case spectator_join:
                    if (username == null) break;
                    try {
                        int sid = (Integer) msg.data;
                        GameSession found = null;
                        for (GameSession gs : activeSessions) {
                            if (gs.sessionId == sid) {
                                found = gs;
                                break;
                            }
                        }
                        if (found == null) {
                            sendMessage(new Message(Message.MessageType.spectator_fail, "Game not found."));
                            return;
                        }
                        if (found.player1 == this || found.player2 == this) {
                            sendMessage(new Message(Message.MessageType.spectator_fail, "You are playing that game."));
                            return;
                        }
                        waitingQueue.remove(this);
                        currentGame = found;
                        isSpectator = true;
                        found.addSpectator(this);
                        int rR = userStore.getRating(found.game.redPlayer);
                        int rB = userStore.getRating(found.game.blackPlayer);
                        Object[] payload = new Object[]{
                                found.game.board,
                                new int[]{rR, rB},
                                new int[]{found.getRedSeconds(), found.getBlackSeconds()},
                                found.game.redPlayer,
                                found.game.blackPlayer,
                                found.game.currentPlayer
                        };
                        sendMessage(new Message(Message.MessageType.spectator_ok, payload));
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.spectator_fail, "Could not spectate."));
                    }
                    break;

                case username:
                    String name = (String) msg.data;
                    for (ClientThread c : clients) {
                        if (c != this && name.equals(c.username)) {
                            sendMessage(new Message(Message.MessageType.username_taken, null));
                            return;
                        }
                    }
                    this.username = name;
                    sendMessage(new Message(Message.MessageType.username_ok, null));
                    waitingQueue.add(this);
                    sendMessage(new Message(Message.MessageType.waiting, null));
                    callback.accept(username + " joined the queue");
                    matchPlayers();
                    break;

                case move:
                    if (currentGame != null && !isSpectator) {
                        currentGame.handleMove((Move) msg.data, this);
                    }
                    break;

                case chat:
                    if (currentGame != null) {
                        String prefix = isSpectator ? "[Spectator] " : "";
                        currentGame.broadcastToGame(new Message(Message.MessageType.chat,
                                prefix + username + ": " + msg.data));
                    }
                    break;

                case resign:
                    if (currentGame != null && !isSpectator) {
                        String opponent = currentGame.player1 == this ? currentGame.player2.username :
                                currentGame.player1.username;
                        finishGameWithResult(currentGame, opponent);
                    }
                    break;

                case draw_offer:
                    if (currentGame != null && !isSpectator) {
                        ClientThread opponent = currentGame.player1 == this ? currentGame.player2 :
                                currentGame.player1;
                        opponent.sendMessage(new Message(Message.MessageType.draw_offer, username));
                    }
                    break;

                case draw_accept:
                    if (currentGame != null && !isSpectator) {
                        finishGameWithResult(currentGame, "draw");
                    }
                    break;

                case draw_decline:
                    if (currentGame != null && !isSpectator) {
                        ClientThread opp = currentGame.player1 == this ? currentGame.player2 :
                                currentGame.player1;
                        opp.sendMessage(new Message(Message.MessageType.draw_decline, null));
                    }
                    break;

                case play_again:
                    if (isSpectator || username == null || lastOpponentName == null) break;
                    for (ClientThread c : clients) {
                        if (lastOpponentName.equals(c.username)) {
                            c.sendMessage(new Message(Message.MessageType.play_again, username));
                            break;
                        }
                    }
                    break;

                case play_again_ack:
                    if (isSpectator) break;
                    waitingQueue.remove(this);
                    waitingQueue.add(this);
                    sendMessage(new Message(Message.MessageType.waiting, null));
                    matchPlayers();
                    break;

                default:
                    callback.accept("Unknown message from " + username);
                    break;
            }
        }
    }
}
