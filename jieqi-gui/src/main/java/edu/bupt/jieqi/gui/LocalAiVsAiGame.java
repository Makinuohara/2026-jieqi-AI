package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.ai.Agent;
import edu.bupt.jieqi.ai.GreedyAgent;
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
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LocalAiVsAiGame {
    private static final SearchBudget AI_BUDGET =
            new SearchBudget(Duration.ofSeconds(2), 1);

    private final GameEngine engine;
    private final Agent redAi;
    private final Agent blackAi;
    private final List<String> moveRecords = new ArrayList<>();
    private GameState state;

    public LocalAiVsAiGame() {
        this(new StandardGameEngine(), new GreedyAgent(), new RandomAgent());
    }

    public LocalAiVsAiGame(GameEngine engine, Agent redAi, Agent blackAi) {
        this.engine = Objects.requireNonNull(engine);
        this.redAi = Objects.requireNonNull(redAi);
        this.blackAi = Objects.requireNonNull(blackAi);
        this.state = GameState.initial();
    }

    public GameState state() {
        return state;
    }

    public String redAiName() {
        return redAi.name();
    }

    public String blackAiName() {
        return blackAi.name();
    }

    public List<String> moveRecords() {
        return List.copyOf(moveRecords);
    }

    public boolean isInCheck(Color color) {
        return state.status() == GameStatus.PLAYING
                && engine.isInCheck(state, color);
    }

    public void restart() {
        state = GameState.initial();
        moveRecords.clear();
    }

    public Optional<ApplyResult> performNextMove() {
        if (state.status() != GameStatus.PLAYING) {
            return Optional.empty();
        }

        List<Move> legalMoves = engine.legalMoves(state);
        if (legalMoves.isEmpty()) {
            GameStatus result = state.currentTurn() == Color.RED
                    ? GameStatus.BLACK_WIN
                    : GameStatus.RED_WIN;
            Color stuckSide = state.currentTurn();
            state = withStatus(state, result);
            moveRecords.add(colorText(stuckSide) + "没有合法走法，"
                    + colorText(stuckSide.opposite()) + "获胜");
            return Optional.empty();
        }

        Color mover = state.currentTurn();
        Agent currentAgent = mover == Color.RED ? redAi : blackAi;
        PlayerView view = PlayerView.from(state, mover, legalMoves);
        Move selected = currentAgent.chooseMove(view, AI_BUDGET);
        ApplyResult result = applyAndRecord(selected, mover, currentAgent.name());
        if (!result.validation().valid()) {
            throw new IllegalStateException("人工智能提交了非法走法");
        }
        return Optional.of(result);
    }

    private ApplyResult applyAndRecord(Move move, Color mover, String agentName) {
        ApplyResult result = engine.apply(state, move);
        if (result.validation().valid()) {
            state = result.state();
            StringBuilder record = new StringBuilder(colorText(mover))
                    .append("（").append(agentName).append("）：")
                    .append(positionText(move.source()))
                    .append(" → ")
                    .append(positionText(move.destination()));
            result.events().stream()
                    .filter(GameEvent.PieceRevealed.class::isInstance)
                    .map(GameEvent.PieceRevealed.class::cast)
                    .findFirst()
                    .ifPresent(event -> record.append("，翻出")
                            .append(LocalHumanVsAiGame.pieceName(event.type(), mover)));
            moveRecords.add(record.toString());
        }
        return result;
    }

    private GameState withStatus(GameState current, GameStatus status) {
        return new GameState(
                current.board(),
                current.currentTurn(),
                current.noCaptureHalfMoves(),
                current.consecutiveCheckCount(),
                current.consecutiveChaseCount(),
                current.turnStartedAt(),
                status,
                current.redHiddenPool(),
                current.blackHiddenPool());
    }

    private String positionText(Position position) {
        return (position.file() + 1) + "列" + (position.rank() + 1) + "行";
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }
}
