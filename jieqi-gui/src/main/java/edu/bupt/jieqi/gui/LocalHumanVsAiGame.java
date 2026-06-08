package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.ai.Agent;
import edu.bupt.jieqi.ai.RandomAgent;
import edu.bupt.jieqi.ai.SearchBudget;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.GameEvent;
import edu.bupt.jieqi.rules.MoveError;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LocalHumanVsAiGame {
    private static final SearchBudget AI_BUDGET =
            new SearchBudget(Duration.ofSeconds(2), 1);

    private final GameEngine engine;
    private final Agent ai;
    private final List<String> moveRecords = new ArrayList<>();
    private GameState state;

    public LocalHumanVsAiGame() {
        this(new StandardGameEngine(), new RandomAgent());
    }

    public LocalHumanVsAiGame(GameEngine engine, Agent ai) {
        this.engine = Objects.requireNonNull(engine);
        this.ai = Objects.requireNonNull(ai);
        this.state = GameState.initial();
    }

    public GameState state() {
        return state;
    }

    public List<Move> legalHumanMoves() {
        if (state.status() != GameStatus.PLAYING || state.currentTurn() != Color.RED) {
            return List.of();
        }
        return engine.legalMoves(state);
    }

    public ApplyResult submitHumanMove(Move move) {
        if (state.currentTurn() != Color.RED) {
            return ApplyResult.rejected(
                    state, MoveError.NOT_YOUR_TURN, "现在不是红方回合");
        }
        return applyAndRecord(move, "红方");
    }

    public Optional<ApplyResult> performAiMove() {
        if (state.status() != GameStatus.PLAYING || state.currentTurn() != Color.BLACK) {
            return Optional.empty();
        }

        List<Move> legalMoves = engine.legalMoves(state);
        if (legalMoves.isEmpty()) {
            state = withStatus(state, GameStatus.RED_WIN);
            moveRecords.add("黑方没有合法走法，红方获胜");
            return Optional.empty();
        }

        PlayerView view = PlayerView.from(state, Color.BLACK, legalMoves);
        Move selected = ai.chooseMove(view, AI_BUDGET);
        ApplyResult result = applyAndRecord(selected, "黑方");
        if (!result.validation().valid()) {
            throw new IllegalStateException("人工智能提交了非法走法");
        }
        return Optional.of(result);
    }

    public void resignHuman() {
        if (state.status() == GameStatus.PLAYING) {
            state = withStatus(state, GameStatus.BLACK_WIN);
            moveRecords.add("红方认输，黑方获胜");
        }
    }

    public void restart() {
        state = GameState.initial();
        moveRecords.clear();
    }

    public List<String> moveRecords() {
        return List.copyOf(moveRecords);
    }

    public boolean isInCheck(Color color) {
        return state.status() == GameStatus.PLAYING
                && engine.isInCheck(state, color);
    }

    private ApplyResult applyAndRecord(Move move, String side) {
        Color mover = state.currentTurn();
        ApplyResult result = engine.apply(state, move);
        if (result.validation().valid()) {
            state = result.state();
            StringBuilder record = new StringBuilder(side)
                    .append("：")
                    .append(positionText(move.source()))
                    .append(" → ")
                    .append(positionText(move.destination()));
            result.events().stream()
                    .filter(GameEvent.PieceRevealed.class::isInstance)
                    .map(GameEvent.PieceRevealed.class::cast)
                    .findFirst()
                    .ifPresent(event -> record.append("，翻出")
                            .append(pieceName(event.type(), mover)));
            moveRecords.add(record.toString());
        }
        return result;
    }

    private GameState withStatus(GameState current, GameStatus status) {
        return new GameState(
                current.board(),
                current.currentTurn(),
                current.noCaptureHalfMoves(),
                current.redConsecutiveCheckCount(),
                current.redConsecutiveChaseCount(),
                current.redChasedPosition(),
                current.redChasedPieceType(),
                current.blackConsecutiveCheckCount(),
                current.blackConsecutiveChaseCount(),
                current.blackChasedPosition(),
                current.blackChasedPieceType(),
                current.turnStartedAt(),
                status,
                current.redHiddenPool(),
                current.blackHiddenPool());
    }

    private String positionText(Position position) {
        return (position.file() + 1) + "列" + (position.rank() + 1) + "行";
    }

    static String pieceName(edu.bupt.jieqi.model.PieceType type, Color color) {
        return PieceTextFormatter.format(edu.bupt.jieqi.model.Piece.visible(color, type));
    }
}
