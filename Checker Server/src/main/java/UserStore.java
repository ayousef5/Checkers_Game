import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * user|hashHex|rating|wins|losses|draws|friendsCsv|requestsCsv
 * Legacy 3-field lines: wins=losses=draws=0, friends and requests empty.
 */
public class UserStore {

    public static final int DEFAULT_RATING = 1200;
    public static final String BOT_USERNAME = "Bot";
    private final Path filePath;
    private final Map<String, UserRecord> users = new HashMap<>();

    private static class UserRecord {
        String hashHex;
        int rating;
        int wins;
        int losses;
        int draws;
        /** Mutual accepted friends. */
        Set<String> friends = new LinkedHashSet<>();
        /** Pending incoming: other users who requested this user. */
        Set<String> friendRequests = new LinkedHashSet<>();
    }

    public UserStore(String fileName) {
        filePath = Paths.get(fileName);
        synchronized (this) {
            loadFromDisk();
        }
    }

    public synchronized boolean userExists(String username) {
        return users.containsKey(username);
    }

    public synchronized void registerUser(String username, String passwordHashHex) throws IOException {
        if (users.containsKey(username)) {
            throw new IOException("duplicate");
        }
        UserRecord r = new UserRecord();
        r.hashHex = passwordHashHex;
        r.rating = DEFAULT_RATING;
        users.put(username, r);
        saveToDisk();
    }

    public synchronized boolean verifyLogin(String username, String passwordHashHex) {
        UserRecord r = users.get(username);
        if (r == null) return false;
        return r.hashHex.equalsIgnoreCase(passwordHashHex);
    }

    public synchronized int getRating(String username) {
        UserRecord r = users.get(username);
        return r == null ? DEFAULT_RATING : r.rating;
    }

    public synchronized int getWins(String username) {
        UserRecord r = users.get(username);
        return r == null ? 0 : r.wins;
    }

    public synchronized int getLosses(String username) {
        UserRecord r = users.get(username);
        return r == null ? 0 : r.losses;
    }

    public synchronized int getDraws(String username) {
        UserRecord r = users.get(username);
        return r == null ? 0 : r.draws;
    }

    public synchronized int[] getStats(String username) {
        UserRecord r = users.get(username);
        if (r == null) {
            return new int[]{DEFAULT_RATING, 0, 0, 0};
        }
        return new int[]{r.rating, r.wins, r.losses, r.draws};
    }

    public synchronized void setRating(String username, int rating) throws IOException {
        UserRecord r = users.get(username);
        if (r == null) return;
        r.rating = rating;
        saveToDisk();
    }

    public synchronized void updateTwoRatings(String u1, int newR1, String u2, int newR2) throws IOException {
        UserRecord a = users.get(u1);
        UserRecord b = users.get(u2);
        if (a != null) a.rating = newR1;
        if (b != null) b.rating = newR2;
        saveToDisk();
    }

    /**
     * Human vs human. outcome: "draw" or winner's username. newRRed / newRBlack are already computed Elo values.
     */
    public synchronized void recordHumanMatch(String redName, String blackName, int newRRed, int newRBlack,
            String outcome) throws IOException {
        if ("draw".equals(outcome)) {
            UserRecord ur = users.get(redName);
            UserRecord ub = users.get(blackName);
            if (ur != null) {
                ur.rating = newRRed;
                ur.draws++;
            }
            if (ub != null) {
                ub.rating = newRBlack;
                ub.draws++;
            }
        } else {
            String w = outcome;
            String l = w.equals(redName) ? blackName : redName;
            int wNew = w.equals(redName) ? newRRed : newRBlack;
            int lNew = l.equals(redName) ? newRRed : newRBlack;
            UserRecord uw = users.get(w);
            UserRecord ul = users.get(l);
            if (uw != null) {
                uw.rating = wNew;
                uw.wins++;
            }
            if (ul != null) {
                ul.rating = lNew;
                ul.losses++;
            }
        }
        saveToDisk();
    }

    /** One human vs Bot(1200). outcome: "draw", human name if human won, or {@link #BOT_USERNAME} if bot won. */
    public synchronized void recordBotMatch(String human, String outcome) throws IOException {
        UserRecord u = users.get(human);
        if (u == null) return;
        if ("draw".equals(outcome)) {
            u.rating = UserStore.computeNewRating(u.rating, DEFAULT_RATING, 0.5);
            u.draws++;
        } else if (outcome.equals(human)) {
            u.rating = UserStore.computeNewRating(u.rating, DEFAULT_RATING, 1.0);
            u.wins++;
        } else {
            u.rating = UserStore.computeNewRating(u.rating, DEFAULT_RATING, 0.0);
            u.losses++;
        }
        saveToDisk();
    }

    private static boolean isRealUser(String name) {
        return name != null && !name.isEmpty() && !BOT_USERNAME.equals(name);
    }

    public synchronized List<String> getFriendsList(String u) {
        UserRecord r = users.get(u);
        if (r == null) return new ArrayList<>();
        return new ArrayList<>(r.friends);
    }

    public synchronized List<String> getIncomingFriendRequests(String u) {
        UserRecord r = users.get(u);
        if (r == null) return new ArrayList<>();
        return new ArrayList<>(r.friendRequests);
    }

    /** from requests to befriend to; to must have user record. */
    public synchronized void addIncomingFriendRequest(String to, String from) throws IOException {
        if (!isRealUser(to) || !isRealUser(from) || to.equals(from)) {
            return;
        }
        UserRecord t = users.get(to);
        if (t == null) return;
        if (t.friends.contains(from)) return;
        if (t.friendRequests.contains(from)) return;
        t.friendRequests.add(from);
        saveToDisk();
    }

    public synchronized void removeIncomingRequest(String to, String from) throws IOException {
        UserRecord t = users.get(to);
        if (t == null) return;
        t.friendRequests.remove(from);
        saveToDisk();
    }

    public synchronized boolean areFriends(String a, String b) {
        if (a == null || b == null) return false;
        UserRecord ra = users.get(a);
        return ra != null && ra.friends.contains(b);
    }

    public synchronized boolean hasIncomingRequestFrom(String to, String from) {
        UserRecord t = users.get(to);
        return t != null && t.friendRequests.contains(from);
    }

    public synchronized void acceptFriendship(String accepter, String requester) throws IOException {
        if (!isRealUser(accepter) || !isRealUser(requester)) {
            return;
        }
        UserRecord A = users.get(accepter);
        UserRecord B = users.get(requester);
        if (A == null || B == null) return;
        if (!A.friendRequests.remove(requester)) return;
        A.friends.add(requester);
        B.friends.add(accepter);
        B.friendRequests.remove(accepter);
        saveToDisk();
    }

    public synchronized void endFriendship(String a, String b) throws IOException {
        UserRecord ra = users.get(a);
        UserRecord rb = users.get(b);
        if (ra != null) {
            ra.friends.remove(b);
        }
        if (rb != null) {
            rb.friends.remove(a);
        }
        saveToDisk();
    }

    public static String sha256Hex(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static int computeNewRating(int playerRating, int opponentRating, double actualScore) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - playerRating) / 400.0));
        return (int) Math.round(playerRating + 32.0 * (actualScore - expected));
    }

    private void loadFromDisk() {
        users.clear();
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 3) continue;
                UserRecord r = new UserRecord();
                r.hashHex = p[1];
                r.rating = safeInt(p, 2, DEFAULT_RATING);
                r.wins = p.length > 3 ? safeInt(p, 3, 0) : 0;
                r.losses = p.length > 4 ? safeInt(p, 4, 0) : 0;
                r.draws = p.length > 5 ? safeInt(p, 5, 0) : 0;
                r.friends = parseCsvSet(p, 6);
                r.friendRequests = parseCsvSet(p, 7);
                users.put(p[0], r);
            }
        } catch (Exception e) {
            users.clear();
        }
    }

    private static int safeInt(String[] p, int i, int def) {
        if (i >= p.length) return def;
        try {
            return Integer.parseInt(p[i].trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static Set<String> parseCsvSet(String[] p, int index) {
        if (p.length <= index) return new LinkedHashSet<>();
        String s = p[index].trim();
        if (s.isEmpty()) return new LinkedHashSet<>();
        String[] parts = s.split(",");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String x : parts) {
            x = x.trim();
            if (!x.isEmpty() && isRealUser(x)) {
                out.add(x);
            }
        }
        return out;
    }

    private void saveToDisk() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, UserRecord> e : users.entrySet()) {
            UserRecord u = e.getValue();
            sb.append(e.getKey()).append('|').append(u.hashHex).append('|')
                    .append(u.rating).append('|')
                    .append(u.wins).append('|')
                    .append(u.losses).append('|')
                    .append(u.draws).append('|')
                    .append(joinSet(u.friends)).append('|')
                    .append(joinSet(u.friendRequests)).append('\n');
        }
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String joinSet(Set<String> s) {
        if (s == null || s.isEmpty()) return "";
        return String.join(",", s);
    }
}
