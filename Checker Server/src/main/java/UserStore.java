import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserStore {

    private static final int DEFAULT_RATING = 1200;
    private final Path filePath;
    private final Map<String, UserRecord> users = new HashMap<>();

    private static class UserRecord {
        String hashHex;
        int rating;
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
                String[] p = line.split("\\|");
                if (p.length != 3) continue;
                UserRecord r = new UserRecord();
                r.hashHex = p[1];
                r.rating = Integer.parseInt(p[2]);
                users.put(p[0], r);
            }
        } catch (Exception e) {
            users.clear();
        }
    }

    private void saveToDisk() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, UserRecord> e : users.entrySet()) {
            sb.append(e.getKey()).append('|').append(e.getValue().hashHex).append('|')
                    .append(e.getValue().rating).append('\n');
        }
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);
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
}
