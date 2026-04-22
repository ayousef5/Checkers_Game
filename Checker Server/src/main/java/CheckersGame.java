import java.util.ArrayList; // needed for lists

public class CheckersGame { // handles all game logic

    public Board board; // the game board
    public String currentPlayer; // "red" or "black"
    public String redPlayer; // red player's username
    public String blackPlayer; // black player's username
    public boolean gameOver; // true if game has ended
    public String winner; // username of winner, null if ongoing

    public CheckersGame(String redPlayer, String blackPlayer) { // constructor
        this.board = new Board(); // create fresh board
        this.redPlayer = redPlayer; // set red player
        this.blackPlayer = blackPlayer; // set black player
        this.currentPlayer = "black"; // black moves first
        this.gameOver = false; // game is not over
        this.winner = null; // no winner yet
    }

    /** Copy for AI search (independent board state). */
    public CheckersGame copy() {
        CheckersGame c = new CheckersGame(redPlayer, blackPlayer);
        c.board = Board.copyOf(this.board);
        c.currentPlayer = this.currentPlayer;
        c.gameOver = this.gameOver;
        c.winner = this.winner;
        return c;
    }

    public ArrayList<Move> getValidMoves(String color) { // get all legal moves for a color
        ArrayList<Move> moves = new ArrayList<>(); // list to return
        ArrayList<Move> jumps = new ArrayList<>(); // jumps found

        for (int row = 0; row < 8; row++) { // check every row
            for (int col = 0; col < 8; col++) { // check every column
                Piece piece = board.getPiece(row, col); // get piece at square
                if (piece != null && piece.color.equals(color)) { // if it belongs to current player
                    jumps.addAll(getJumpsForPiece(piece)); // collect jumps
                    moves.addAll(getMovesForPiece(piece)); // collect regular moves
                }
            }
        }

        if (!jumps.isEmpty()) return jumps; // jumps are mandatory
        return moves; // return regular moves if no jumps
    }

    private ArrayList<Move> getMovesForPiece(Piece piece) { // get regular moves for one piece
        ArrayList<Move> moves = new ArrayList<>(); // list to return
        int[][] dirs = getDirections(piece); // get allowed directions

        for (int[] dir : dirs) { // check each direction
            int newRow = piece.row + dir[0]; // calculate new row
            int newCol = piece.col + dir[1]; // calculate new col
            if (inBounds(newRow, newCol) && board.getPiece(newRow, newCol) == null) { // if empty
                moves.add(new Move(piece.row, piece.col, newRow, newCol)); // valid move
            }
        }
        return moves; // return found moves
    }

    private ArrayList<Move> getJumpsForPiece(Piece piece) { // get all jumps for one piece
        ArrayList<Move> jumps = new ArrayList<>(); // list to return
        getJumpsRecursive(piece.row, piece.col, piece, new Move(piece.row, piece.col, piece.row, piece.col),
                jumps, new boolean[8][8]);
        return jumps; // return all jump chains found
    }

    private void getJumpsRecursive(int row, int col, Piece piece, Move currentMove, ArrayList<Move> jumps,
                                   boolean[][] captured) { // find multi-jumps
        int[][] dirs = getDirections(piece); // get allowed directions
        boolean foundJump = false; // track if any jump was found

        for (int[] dir : dirs) { // check each direction
            int midRow = row + dir[0]; // middle square row
            int midCol = col + dir[1]; // middle square col
            int landRow = row + dir[0] * 2; // landing square row
            int landCol = col + dir[1] * 2; // landing square col

            if (!inBounds(landRow, landCol)) continue; // skip if out of bounds
            Piece middle = board.getPiece(midRow, midCol); // get middle piece
            if (middle == null || middle.color.equals(piece.color)) continue; // must be opponent
            if (captured[midRow][midCol]) continue; // already captured this piece
            if (board.getPiece(landRow, landCol) != null) continue; // landing must be empty

            foundJump = true; // found a valid jump
            captured[midRow][midCol] = true; // mark as captured

            Move nextMove = new Move(currentMove.fromRow, currentMove.fromCol, landRow, landCol); // build move
            nextMove.capturedPositions.addAll(currentMove.capturedPositions); // carry over captures
            nextMove.addCapture(midRow, midCol); // add this capture

            getJumpsRecursive(landRow, landCol, piece, nextMove, jumps, captured); // continue chain
            captured[midRow][midCol] = false; // unmark for backtracking
        }

        if (!foundJump && !currentMove.capturedPositions.isEmpty()) { // no more jumps available
            jumps.add(currentMove); // add completed jump chain
        }
    }

    public boolean isValidMove(Move move, String color) { // check if a move is legal
        ArrayList<Move> valid = getValidMoves(color); // get all legal moves
        for (Move m : valid) { // check each valid move
            if (m.fromRow == move.fromRow && m.fromCol == move.fromCol &&
                    m.toRow == move.toRow && m.toCol == move.toCol) { // if it matches
                move.capturedPositions = m.capturedPositions; // copy captured positions
                return true; // move is valid
            }
        }
        return false; // move not found
    }

    public void applyMove(Move move) { // apply a validated move to the board
        board.movePiece(move.fromRow, move.fromCol, move.toRow, move.toCol); // move piece

        for (int[] pos : move.capturedPositions) { // remove all captured pieces
            board.removePiece(pos[0], pos[1]); // remove captured piece
        }

        Piece piece = board.getPiece(move.toRow, move.toCol); // get moved piece
        if (piece.color.equals("black") && move.toRow == 7) piece.isKing = true; // black kinged
        if (piece.color.equals("red") && move.toRow == 0) piece.isKing = true; // red kinged

        currentPlayer = currentPlayer.equals("black") ? "red" : "black"; // switch turns

        if (isGameOver()) { // check if game ended
            gameOver = true; // mark game over
            winner = currentPlayer.equals("black") ? redPlayer : blackPlayer; // set winner
        }
    }

    public boolean isGameOver() { // check if current player has no moves
        return getValidMoves(currentPlayer).isEmpty(); // no moves means game over
    }

    private int[][] getDirections(Piece piece) { // get movement directions for a piece
        if (piece.isKing) return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}; // all 4 dirs
        if (piece.color.equals("black")) return new int[][]{{1, -1}, {1, 1}}; // black moves down
        return new int[][]{{-1, -1}, {-1, 1}}; // red moves up
    }

    private boolean inBounds(int row, int col) { // check if position is on the board
        return row >= 0 && row < 8 && col >= 0 && col < 8; // must be within 8x8
    }
}