import java.io.Serializable;

public class Piece implements Serializable {
    static final long serialVersionUID = 42L;

    public String color;
    public boolean isKing;
    public int row;
    public int col;

    // board square occupant
    public Piece(String color, int row, int col) {
        this.color = color;
        this.row = row;
        this.col = col;
        this.isKing = false;
    }
}
