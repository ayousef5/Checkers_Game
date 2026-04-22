import java.io.Serializable;

public class GameListEntry implements Serializable {
    static final long serialVersionUID = 42L;

    public int sessionId;
    public String playerRed;
    public String playerBlack;
    public int ratingRed;
    public int ratingBlack;

    public GameListEntry(int sessionId, String playerRed, String playerBlack, int ratingRed, int ratingBlack) {
        this.sessionId = sessionId;
        this.playerRed = playerRed;
        this.playerBlack = playerBlack;
        this.ratingRed = ratingRed;
        this.ratingBlack = ratingBlack;
    }

    @Override
    public String toString() {
        return "#" + sessionId + "  " + playerRed + " (" + ratingRed + ")  vs  " + playerBlack + " (" + ratingBlack + ")";
    }
}
