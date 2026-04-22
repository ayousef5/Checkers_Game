import java.util.ArrayList;
import java.util.Comparator;

public final class CheckersAI {

    public static final int MINIMAX_DEPTH = 4;

    private CheckersAI() {
    }

    // root move search
    public static Move chooseMove(CheckersGame state, String aiColor) {
        ArrayList<Move> moves = state.getValidMoves(aiColor);
        if (moves.isEmpty()) {
            return null;
        }
        Move best = null;
        int bestVal = Integer.MIN_VALUE;
        for (Move m : orderMoves(moves)) {
            CheckersGame next = state.copy();
            next.applyMove(m);
            int v = minimax(next, MINIMAX_DEPTH - 1, aiColor, next.currentPlayer);
            if (v > bestVal) {
                bestVal = v;
                best = m;
            }
        }
        return best != null ? best : moves.get(0);
    }

    // minimax search
    private static int minimax(CheckersGame state, int depth, String aiColor, String toMove) {
        if (state.gameOver) {
            return terminalScore(state, aiColor);
        }
        if (depth == 0) {
            return evaluate(state, aiColor);
        }
        boolean maxNode = toMove.equals(aiColor);
        ArrayList<Move> moves = state.getValidMoves(toMove);
        if (moves.isEmpty()) {
            if (toMove.equals(aiColor)) {
                return -1_000_000;
            }
            return 1_000_000;
        }
        if (maxNode) {
            int v = Integer.MIN_VALUE;
            for (Move m : orderMoves(moves)) {
                CheckersGame next = state.copy();
                next.applyMove(m);
                v = Math.max(v, minimax(next, depth - 1, aiColor, next.currentPlayer));
            }
            return v;
        } else {
            int v = Integer.MAX_VALUE;
            for (Move m : orderMoves(moves)) {
                CheckersGame next = state.copy();
                next.applyMove(m);
                v = Math.min(v, minimax(next, depth - 1, aiColor, next.currentPlayer));
            }
            return v;
        }
    }

    // terminal or cutoff
    private static int terminalScore(CheckersGame g, String aiColor) {
        if (!g.gameOver) {
            return evaluate(g, aiColor);
        }
        String w = g.winner;
        if (w == null) {
            return 0;
        }
        boolean iWon = (g.redPlayer != null && w.equals(g.redPlayer) && "red".equals(aiColor))
                || (g.blackPlayer != null && w.equals(g.blackPlayer) && "black".equals(aiColor));
        return iWon ? 1_000_000 : -1_000_000;
    }

    // static evaluation
    private static int evaluate(CheckersGame g, String forColor) {
        if (g.gameOver) {
            return terminalScore(g, forColor);
        }
        int myMen = 0, opMen = 0, myK = 0, opK = 0;
        int myAdv = 0, opAdv = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = g.board.getPiece(r, c);
                if (p == null) continue;
                boolean mine = p.color.equals(forColor);
                if (p.isKing) {
                    if (mine) myK++;
                    else opK++;
                } else {
                    if (mine) myMen++;
                    else opMen++;
                }
                int ar = p.color.equals("black") ? r : (7 - r);
                if (mine) myAdv += ar;
                else opAdv += ar;
            }
        }
        return (myMen - opMen) * 100
                + (myK - opK) * 15
                + 2 * (myAdv - opAdv);
    }

    // try jumps first
    private static ArrayList<Move> orderMoves(ArrayList<Move> moves) {
        ArrayList<Move> out = new ArrayList<>(moves);
        out.sort(Comparator
                .comparingInt((Move m) -> m.capturedPositions == null || m.capturedPositions.isEmpty() ? 0 : 1)
                .reversed()
                .thenComparingInt(m -> m.capturedPositions != null ? m.capturedPositions.size() : 0)
                .reversed());
        return out;
    }
}
