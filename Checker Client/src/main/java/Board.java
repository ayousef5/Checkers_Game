import java.io.Serializable;

public class Board implements Serializable {
    static final long serialVersionUID = 42L;

    public Piece[][] grid;

    // standard start
    public Board() {
        grid = new Piece[8][8];
        initializeBoard();
    }

    // dark square setup
    private void initializeBoard() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if ((row + col) % 2 != 0) {
                    grid[row][col] = new Piece("black", row, col);
                }
            }
        }
        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if ((row + col) % 2 != 0) {
                    grid[row][col] = new Piece("red", row, col);
                }
            }
        }
    }

    // cell contents
    public Piece getPiece(int row, int col) {
        return grid[row][col];
    }

    // place and sync coords
    public void setPiece(int row, int col, Piece piece) {
        grid[row][col] = piece;
        if (piece != null) {
            piece.row = row;
            piece.col = col;
        }
    }

    // clear cell
    public void removePiece(int row, int col) {
        grid[row][col] = null;
    }

    // relocate piece
    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        Piece piece = grid[fromRow][fromCol];
        setPiece(toRow, toCol, piece);
        removePiece(fromRow, fromCol);
    }
}
