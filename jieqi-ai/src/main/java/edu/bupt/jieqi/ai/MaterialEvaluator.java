package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PlayerView;

public final class MaterialEvaluator implements Evaluator {
    @Override
    public double evaluate(PlayerView view) {
        return view.board().pieces().values().stream()
                .mapToDouble(piece -> signedValue(piece, view))
                .sum();
    }

    private double signedValue(Piece piece, PlayerView view) {
        double value = piece.visible() ? piece.actualType().baseValue() : expectedHiddenValue();
        return piece.owner() == view.perspective() ? value : -value;
    }

    private double expectedHiddenValue() {
        return (2 * 600 + 2 * 270 + 2 * 285 + 5 * 30 + 2 * 120 + 2 * 120) / 15.0;
    }
}

