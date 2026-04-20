import java.io.Serializable; // needed for network transfer

public class Piece implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public String color; // "red" or "black"
    public boolean isKing; // true if piece is a king
    public int row; // current row on the board
    public int col; // current column on the board

    public Piece(String color, int row, int col) { // constructor
        this.color = color; // set color
        this.row = row; // set row
        this.col = col; // set col
        this.isKing = false; // pieces start as regular
    }
}