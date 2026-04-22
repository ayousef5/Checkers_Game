import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class Server {

    int count = 1;
    ArrayList<ClientThread> clients = new ArrayList<>();
    ArrayList<ClientThread> waitingQueue = new ArrayList<>();
    ArrayList<GameSession> activeSessions = new ArrayList<>();
    TheServer server;
    private Consumer<Serializable> callback;
    private final UserStore userStore = new UserStore("users.txt");
    private final Set<String> connectedUsernames = new HashSet<>();
    private final Object connectLock = new Object();

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

    public void runBotPly(GameSession session) {
        session.applyBotMove();
    }

    public void finishGameWithResult(GameSession session, String outcome) {
        synchronized (session) {
            if (!session.tryMarkFinished()) {
                return;
            }
            session.stopTurnTimer();
        }
        try {
            if (session.vsBot) {
                String hum = session.player1.username;
                if ("draw".equals(outcome)) {
                    userStore.recordBotMatch(hum, "draw");
                } else if (outcome.equals(hum)) {
                    userStore.recordBotMatch(hum, hum);
                } else {
                    userStore.recordBotMatch(hum, UserStore.BOT_USERNAME);
                }
            } else if ("draw".equals(outcome)) {
                String r = session.game.redPlayer;
                String b = session.game.blackPlayer;
                int rr0 = userStore.getRating(r);
                int br0 = userStore.getRating(b);
                int nr = UserStore.computeNewRating(rr0, br0, 0.5);
                int nb = UserStore.computeNewRating(br0, rr0, 0.5);
                userStore.recordHumanMatch(r, b, nr, nb, "draw");
            } else {
                String w = outcome;
                String r = session.game.redPlayer;
                String b = session.game.blackPlayer;
                int rr0 = userStore.getRating(r);
                int br0 = userStore.getRating(b);
                if (w.equals(r)) {
                    int nr = UserStore.computeNewRating(rr0, br0, 1.0);
                    int nbl = UserStore.computeNewRating(br0, rr0, 0.0);
                    userStore.recordHumanMatch(r, b, nr, nbl, w);
                } else {
                    int nbw = UserStore.computeNewRating(br0, rr0, 1.0);
                    int nrl = UserStore.computeNewRating(rr0, br0, 0.0);
                    userStore.recordHumanMatch(r, b, nrl, nbw, w);
                }
            }
        } catch (Exception e) {
            callback.accept("Rating save failed: " + e.getMessage());
        }

        session.broadcastToGame(new Message(Message.MessageType.game_over, outcome));

        try {
            if (session.vsBot) {
                int[] s = userStore.getStats(session.player1.username);
                session.player1.rating = s[0];
                session.player1.sendMessage(
                        new Message(Message.MessageType.rating_update, new int[]{s[0], s[1], s[2], s[3]}));
            } else {
                int[] s1 = userStore.getStats(session.player1.username);
                int[] s2 = userStore.getStats(session.player2.username);
                session.player1.rating = s1[0];
                session.player2.rating = s2[0];
                session.player1.sendMessage(
                        new Message(Message.MessageType.rating_update, new int[]{s1[0], s1[1], s1[2], s1[3]}));
                session.player2.sendMessage(
                        new Message(Message.MessageType.rating_update, new int[]{s2[0], s2[1], s2[2], s2[3]}));
            }
        } catch (Exception e) {
            callback.accept("Stats push failed: " + e.getMessage());
        }

        session.endGameCleanup();
        activeSessions.remove(session);
    }

    public void removeClient(ClientThread client) {
        String u = client.username;
        if (u != null) {
            synchronized (connectLock) {
                connectedUsernames.remove(u);
            }
            pushFriendOnline(u, false);
        }
        clients.remove(client);
        waitingQueue.remove(client);
        GameSession g = client.currentGame;
        if (g != null) {
            if (client.isSpectator) {
                g.removeSpectator(client);
                client.currentGame = null;
                client.isSpectator = false;
            } else {
                if (g.vsBot) {
                    if (g.player1 == client) {
                        finishGameWithResult(g, UserStore.BOT_USERNAME);
                    }
                } else {
                    ClientThread opp = g.player1 == client ? g.player2 : g.player1;
                    finishGameWithResult(g, opp.username);
                }
            }
        }
        callback.accept((client.username != null ? client.username : "Client") + " disconnected");
        refreshAllFriendStates();
    }

    private ClientThread findByUsername(String uname) {
        if (uname == null) {
            return null;
        }
        for (ClientThread c : clients) {
            if (uname.equals(c.username)) {
                return c;
            }
        }
        return null;
    }

    // track login and tell friends
    private void addConnectedUser(String name) {
        if (name == null) {
            return;
        }
        synchronized (connectLock) {
            connectedUsernames.add(name);
        }
        pushFriendOnline(name, true);
    }

    // friend list presence push
    private void pushFriendOnline(String u, boolean online) {
        if (u == null) {
            return;
        }
        try {
            for (String f : userStore.getFriendsList(u)) {
                boolean fOnline;
                synchronized (connectLock) {
                    fOnline = connectedUsernames.contains(f);
                }
                if (!fOnline) {
                    continue;
                }
                ClientThread friendClient = findByUsername(f);
                if (friendClient != null) {
                    friendClient.sendMessage(new Message(Message.MessageType.online_status,
                            new Object[]{u, online}));
                }
            }
        } catch (Exception e) {
            callback.accept("online_status: " + e.getMessage());
        }
    }

    private void sendFriendState(ClientThread c) {
        if (c == null || c.username == null) {
            return;
        }
        try {
            ArrayList<String> online = new ArrayList<>();
            for (String f : userStore.getFriendsList(c.username)) {
                boolean on;
                synchronized (connectLock) {
                    on = connectedUsernames.contains(f);
                }
                if (on) {
                    online.add(f);
                }
            }
            c.sendMessage(new Message(Message.MessageType.friend_list_online, online));
            c.sendMessage(new Message(Message.MessageType.friend_pending_list,
                    new ArrayList<>(userStore.getIncomingFriendRequests(c.username))));
        } catch (Exception e) {
            callback.accept("Friend state: " + e.getMessage());
        }
    }

    private void refreshAllFriendStates() {
        for (ClientThread c : new ArrayList<>(clients)) {
            if (c.username != null) {
                sendFriendState(c);
            }
        }
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
                        int[] stReg = userStore.getStats(name);
                        this.rating = stReg[0];
                        sendMessage(new Message(Message.MessageType.auth_ok, stReg));
                        Server.this.addConnectedUser(name);
                        refreshAllFriendStates();
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
                        int[] stIn = userStore.getStats(name);
                        this.rating = stIn[0];
                        sendMessage(new Message(Message.MessageType.auth_ok, stIn));
                        Server.this.addConnectedUser(name);
                        refreshAllFriendStates();
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.auth_fail, "Could not log in."));
                    }
                    break;

                case play_vs_bot:
                    if (username == null || isSpectator) {
                        break;
                    }
                    waitingQueue.remove(this);
                    {
                        GameSession botSession = new GameSession(Server.this, this, count++);
                        activeSessions.add(botSession);
                        this.currentGame = botSession;
                        this.lastOpponentName = UserStore.BOT_USERNAME;
                        botSession.startGame();
                        callback.accept(username + " started vs Bot");
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
                    Server.this.addConnectedUser(name);
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

                case friend_add: {
                    if (username == null) {
                        break;
                    }
                    String targetU = (String) msg.data;
                    if (targetU == null) {
                        targetU = "";
                    } else {
                        targetU = targetU.trim();
                    }
                    if (targetU.isEmpty() || UserStore.BOT_USERNAME.equalsIgnoreCase(targetU)
                            || targetU.equals(username)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Invalid username."));
                        break;
                    }
                    if (!userStore.userExists(targetU)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "No such user."));
                        break;
                    }
                    if (userStore.areFriends(username, targetU)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Already friends."));
                        break;
                    }
                    if (userStore.hasIncomingRequestFrom(username, targetU)) {
                        sendMessage(new Message(Message.MessageType.friend_notice,
                                "You have a pending request from " + targetU + " — use Requests to accept or decline."));
                        break;
                    }
                    if (userStore.getIncomingFriendRequests(targetU).contains(username)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Request already sent."));
                        break;
                    }
                    try {
                        userStore.addIncomingFriendRequest(targetU, username);
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Could not send request."));
                        break;
                    }
                    ClientThread tgt = findByUsername(targetU);
                    if (tgt != null) {
                        tgt.sendMessage(new Message(Message.MessageType.friend_incoming, username));
                        sendFriendState(tgt);
                    }
                    sendMessage(new Message(Message.MessageType.friend_notice, "Friend request sent to " + targetU + "."));
                    break;
                }

                case friend_accept: {
                    if (username == null) {
                        break;
                    }
                    String from = (String) msg.data;
                    if (from == null) {
                        from = "";
                    } else {
                        from = from.trim();
                    }
                    if (from.isEmpty() || !userStore.hasIncomingRequestFrom(username, from)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "No such request."));
                        break;
                    }
                    try {
                        userStore.acceptFriendship(username, from);
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Could not accept."));
                        break;
                    }
                    refreshAllFriendStates();
                    sendMessage(new Message(Message.MessageType.friend_notice, "You are now friends with " + from + "."));
                    ClientThread requester = findByUsername(from);
                    if (requester != null) {
                        requester.sendMessage(new Message(Message.MessageType.friend_notice,
                                username + " accepted your friend request."));
                    }
                    break;
                }

                case friend_decline: {
                    if (username == null) {
                        break;
                    }
                    String dFrom = (String) msg.data;
                    if (dFrom == null) {
                        dFrom = "";
                    } else {
                        dFrom = dFrom.trim();
                    }
                    if (dFrom.isEmpty() || !userStore.hasIncomingRequestFrom(username, dFrom)) {
                        sendMessage(new Message(Message.MessageType.friend_error, "No such request."));
                        break;
                    }
                    try {
                        userStore.removeIncomingRequest(username, dFrom);
                    } catch (Exception e) {
                        sendMessage(new Message(Message.MessageType.friend_error, "Could not update."));
                        break;
                    }
                    sendFriendState(this);
                    break;
                }

                default:
                    callback.accept("Unknown message from " + username);
                    break;
            }
        }
    }
}
