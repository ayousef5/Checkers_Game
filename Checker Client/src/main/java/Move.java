import java.io.Serializable; // needed for network transfer
import java.util.ArrayList;
public class Move implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public int fromRow; // starting row
    public int fromCol; // starting column
    public int toRow; // destination row
    public int toCol; // destination column
    public ArrayList<int[]> capturedPositions; // list of captured piece positions

    public Move(int fromRow, int fromCol, int toRow, int toCol) { // constructor
        this.fromRow = fromRow; // set starting row
        this.fromCol = fromCol; // set starting column
        this.toRow = toRow; // set destination row
        this.toCol = toCol; // set destination column
        this.capturedPositions = new ArrayList<>(); // empty list to start
    }

    public void addCapture(int row, int col) { // add a captured piece position
        capturedPositions.add(new int[]{row, col}); // store row and col
    }
}