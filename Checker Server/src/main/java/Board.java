import java.io.Serializable; // needed for network transfer

public class Board implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public Piece[][] grid; // 8x8 grid of pieces, null means empty square

    public Board() { // constructor
        grid = new Piece[8][8]; // create empty 8x8 board
        initializeBoard(); // place pieces in starting positions
    }

    /** Full deep copy; independent pieces. */
    public static Board copyOf(Board other) {
        Board b = new Board();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                b.grid[r][c] = null;
                Piece p = other.grid[r][c];
                if (p != null) {
                    Piece q = new Piece(p.color, r, c);
                    q.isKing = p.isKing;
                    b.grid[r][c] = q;
                }
            }
        }
        return b;
    }

    private void initializeBoard() { // place pieces on dark squares
        for (int row = 0; row < 3; row++) { // black pieces on rows 0-2
            for (int col = 0; col < 8; col++) { // check every column
                if ((row + col) % 2 != 0) { // only dark squares
                    grid[row][col] = new Piece("black", row, col); // place black piece
                }
            }
        }
        for (int row = 5; row < 8; row++) { // red pieces on rows 5-7
            for (int col = 0; col < 8; col++) { // check every column
                if ((row + col) % 2 != 0) { // only dark squares
                    grid[row][col] = new Piece("red", row, col); // place red piece
                }
            }
        }
    }

    public Piece getPiece(int row, int col) { // get piece at position
        return grid[row][col]; // return piece or null
    }

    public void setPiece(int row, int col, Piece piece) { // place piece at position
        grid[row][col] = piece; // set the square
        if (piece != null) { // update piece's position if not null
            piece.row = row; // update row
            piece.col = col; // update col
        }
    }

    public void removePiece(int row, int col) { // remove piece from position
        grid[row][col] = null; // set square to empty
    }

    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) { // move piece
        Piece piece = grid[fromRow][fromCol]; // get the piece
        setPiece(toRow, toCol, piece); // place it at destination
        removePiece(fromRow, fromCol); // clear original square
    }
}