import java.util.ArrayList;

public class CheckersGame {

    public Board board;
    public String currentPlayer;
    public String redPlayer;
    public String blackPlayer;
    public boolean gameOver;
    public String winner;

    // new game
    public CheckersGame(String redPlayer, String blackPlayer) {
        this.board = new Board();
        this.redPlayer = redPlayer;
        this.blackPlayer = blackPlayer;
        this.currentPlayer = "black";
        this.gameOver = false;
        this.winner = null;
    }

    // legal moves for color
    public ArrayList<Move> getValidMoves(String color) {
        ArrayList<Move> moves = new ArrayList<>();
        ArrayList<Move> jumps = new ArrayList<>();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null && piece.color.equals(color)) {
                    jumps.addAll(getJumpsForPiece(piece));
                    moves.addAll(getMovesForPiece(piece));
                }
            }
        }

        if (!jumps.isEmpty()) return jumps;
        return moves;
    }

    // simple step moves
    private ArrayList<Move> getMovesForPiece(Piece piece) {
        ArrayList<Move> moves = new ArrayList<>();
        int[][] dirs = getDirections(piece);

        for (int[] dir : dirs) {
            int newRow = piece.row + dir[0];
            int newCol = piece.col + dir[1];
            if (inBounds(newRow, newCol) && board.getPiece(newRow, newCol) == null) {
                moves.add(new Move(piece.row, piece.col, newRow, newCol));
            }
        }
        return moves;
    }

    // jump moves from piece
    private ArrayList<Move> getJumpsForPiece(Piece piece) {
        ArrayList<Move> jumps = new ArrayList<>();
        getJumpsRecursive(piece.row, piece.col, piece, new Move(piece.row, piece.col, piece.row, piece.col),
                jumps, new boolean[8][8]);
        return jumps;
    }

    // multi jump dfs
    private void getJumpsRecursive(int row, int col, Piece piece, Move currentMove, ArrayList<Move> jumps,
                                   boolean[][] captured) {
        int[][] dirs = getDirections(piece);
        boolean foundJump = false;

        for (int[] dir : dirs) {
            int midRow = row + dir[0];
            int midCol = col + dir[1];
            int landRow = row + dir[0] * 2;
            int landCol = col + dir[1] * 2;

            if (!inBounds(landRow, landCol)) continue;
            Piece middle = board.getPiece(midRow, midCol);
            if (middle == null || middle.color.equals(piece.color)) continue;
            if (captured[midRow][midCol]) continue;
            if (board.getPiece(landRow, landCol) != null) continue;

            foundJump = true;
            captured[midRow][midCol] = true;

            Move nextMove = new Move(currentMove.fromRow, currentMove.fromCol, landRow, landCol);
            nextMove.capturedPositions.addAll(currentMove.capturedPositions);
            nextMove.addCapture(midRow, midCol);

            getJumpsRecursive(landRow, landCol, piece, nextMove, jumps, captured);
            captured[midRow][midCol] = false;
        }

        if (!foundJump && !currentMove.capturedPositions.isEmpty()) {
            jumps.add(currentMove);
        }
    }

    // list membership
    public boolean isValidMove(Move move, String color) {
        ArrayList<Move> valid = getValidMoves(color);
        for (Move m : valid) {
            if (m.fromRow == move.fromRow && m.fromCol == move.fromCol &&
                    m.toRow == move.toRow && m.toCol == move.toCol) {
                move.capturedPositions = m.capturedPositions;
                return true;
            }
        }
        return false;
    }

    // apply and switch turn
    public void applyMove(Move move) {
        board.movePiece(move.fromRow, move.fromCol, move.toRow, move.toCol);

        for (int[] pos : move.capturedPositions) {
            board.removePiece(pos[0], pos[1]);
        }

        Piece piece = board.getPiece(move.toRow, move.toCol);
        if (piece.color.equals("black") && move.toRow == 7) piece.isKing = true;
        if (piece.color.equals("red") && move.toRow == 0) piece.isKing = true;

        currentPlayer = currentPlayer.equals("black") ? "red" : "black";

        if (isGameOver()) {
            gameOver = true;
            winner = currentPlayer.equals("black") ? redPlayer : blackPlayer;
        }
    }

    // no legal moves
    public boolean isGameOver() {
        return getValidMoves(currentPlayer).isEmpty();
    }

    // forward diagonals
    private int[][] getDirections(Piece piece) {
        if (piece.isKing) return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        if (piece.color.equals("black")) return new int[][]{{1, -1}, {1, 1}};
        return new int[][]{{-1, -1}, {-1, 1}};
    }

    // in bounds
    private boolean inBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
}
