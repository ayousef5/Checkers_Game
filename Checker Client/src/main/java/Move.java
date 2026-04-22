import java.io.Serializable;
import java.util.ArrayList;

public class Move implements Serializable {
    static final long serialVersionUID = 42L;

    public int fromRow;
    public int fromCol;
    public int toRow;
    public int toCol;
    public ArrayList<int[]> capturedPositions;

    // one move with capture list
    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.capturedPositions = new ArrayList<>();
    }

    // mark jumped square
    public void addCapture(int row, int col) {
        capturedPositions.add(new int[]{row, col});
    }
}
