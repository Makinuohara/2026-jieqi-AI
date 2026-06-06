package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import java.util.Comparator;

public final class GreedyAgent implements Agent {
    @Override
    public Move chooseMove(PlayerView view, SearchBudget budget) {
        return view.legalMoves().stream()
                .max(Comparator.comparingInt(move -> view.board().pieceAt(move.destination())
                        .map(piece -> piece.movementType().baseValue())
                        .orElse(0)))
                .orElseThrow(() -> new IllegalStateException("No legal move is available"));
    }

    @Override
    public String name() {
        return "Greedy";
    }
}

