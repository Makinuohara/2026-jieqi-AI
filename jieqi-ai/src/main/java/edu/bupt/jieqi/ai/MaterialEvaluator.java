package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.HiddenPiecePool;
import java.util.Map;

public final class MaterialEvaluator implements Evaluator {
    private static final double MOBILITY_WEIGHT = 2.0;
    private static final double CAPTURE_THREAT_WEIGHT = 0.18;
    private static final double KING_CAPTURE_THREAT = SearchSupport.WIN_SCORE / 2.0;
    private static final double CHECK_PRESSURE = 15_000.0;
    private static final double HANGING_PIECE_WEIGHT = 0.45;

    @Override
    public double evaluate(PlayerView view) {
        return evaluate(
                view,
                SearchSupport.inferHiddenPool(view.board(), Color.RED),
                SearchSupport.inferHiddenPool(view.board(), Color.BLACK));
    }

    @Override
    public double evaluate(PlayerView view, HiddenPiecePool redPool, HiddenPiecePool blackPool) {
        double score = 0.0;
        double redHidden = SearchSupport.expectedHiddenValue(redPool);
        double blackHidden = SearchSupport.expectedHiddenValue(blackPool);

        for (Map.Entry<Position, Piece> entry : view.board().pieces().entrySet()) {
            Piece piece = entry.getValue();
            double hiddenValue = hiddenValue(piece.owner(), redHidden, blackHidden);
            double value = piece.visible() ? piece.actualType().baseValue() : hiddenValue;
            value += positionBonus(entry.getKey(), piece);
            score += piece.owner() == view.perspective() ? value : -value;
        }

        double movePressure = view.legalMoves().stream()
                .mapToDouble(move -> view.board().pieceAt(move.destination())
                        .map(target -> target.visible() && target.actualType() == PieceType.KING
                                ? SearchSupport.WIN_SCORE / 20.0
                                : pieceValue(target, redHidden, blackHidden) * CAPTURE_THREAT_WEIGHT)
                        .orElse(MOBILITY_WEIGHT))
                .sum();
        score += tacticalSafetyScore(view, redPool, blackPool);
        return view.currentTurn() == view.perspective()
                ? score + movePressure
                : score - movePressure;
    }

    private double tacticalSafetyScore(PlayerView view, HiddenPiecePool redPool, HiddenPiecePool blackPool) {
        Color us = view.perspective();
        Color them = us.opposite();
        if (SearchSupport.canCaptureVisibleKing(view, them, us)) {
            return -KING_CAPTURE_THREAT;
        }
        if (SearchSupport.canCaptureVisibleKing(view, us, them)) {
            return KING_CAPTURE_THREAT;
        }

        double score = 0.0;
        if (view.currentTurn() == them) {
            score -= SearchSupport.immediateCaptureValue(view, them, redPool, blackPool) * HANGING_PIECE_WEIGHT;
            if (attacksVisibleKing(view, them, us)) {
                score -= CHECK_PRESSURE;
            }
        } else if (view.currentTurn() == us) {
            score += SearchSupport.immediateCaptureValue(view, us, redPool, blackPool) * HANGING_PIECE_WEIGHT;
            if (attacksVisibleKing(view, us, them)) {
                score += CHECK_PRESSURE;
            }
        }
        return score;
    }

    private boolean attacksVisibleKing(PlayerView view, Color attacker, Color kingOwner) {
        return SearchSupport.canCaptureVisibleKing(view, attacker, kingOwner);
    }

    private double pieceValue(Piece piece, double redHidden, double blackHidden) {
        if (piece.visible()) {
            return piece.actualType().baseValue();
        }
        return hiddenValue(piece.owner(), redHidden, blackHidden);
    }

    private double hiddenValue(Color owner, double redHidden, double blackHidden) {
        return owner == Color.RED ? redHidden : blackHidden;
    }

    private double positionBonus(Position position, Piece piece) {
        PieceType movementType = piece.movementType();
        double center = 4 - Math.abs(position.file() - 4);
        double advancement = piece.owner() == Color.RED
                ? position.rank()
                : 9 - position.rank();
        return switch (movementType) {
            case KING -> 0;
            case ROOK -> center * 3.0;
            case KNIGHT -> center * 4.0 + advancement * 0.6;
            case CANNON -> center * 3.0 + advancement * 0.5;
            case PAWN -> advancement * 4.0 + (advancement >= 5 ? center * 2.0 : 0);
            case GUARD, BISHOP -> center * 2.0 + advancement * 0.4;
        };
    }
}
