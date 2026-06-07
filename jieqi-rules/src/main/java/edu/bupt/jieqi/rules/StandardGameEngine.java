package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class StandardGameEngine implements GameEngine {
    private static final int DRAW_HALF_MOVE_LIMIT = 80;
    private static final int REPETITION_LIMIT = 6;

    private final RandomGenerator random;

    public StandardGameEngine() {
        this(RandomGenerator.getDefault());
    }

    public StandardGameEngine(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public List<Move> legalMoves(GameState state) {
        Objects.requireNonNull(state, "state");
        if (state.status() != GameStatus.PLAYING) {
            return List.of();
        }

        List<Move> moves = new ArrayList<>();
        for (Map.Entry<Position, Piece> entry : state.board().pieces().entrySet()) {
            if (entry.getValue().owner() != state.currentTurn()) {
                continue;
            }
            for (int rank = 0; rank <= 9; rank++) {
                for (int file = 0; file <= 8; file++) {
                    Position destination = new Position(file, rank);
                    if (isLegalMovement(state.board(), entry.getKey(), destination)) {
                        moves.add(new Move(entry.getKey(), destination, 0));
                    }
                }
            }
        }
        return List.copyOf(moves);
    }

    @Override
    public boolean isInCheck(GameState state, Color color) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(color, "color");
        Position king = findKing(state.board(), color);
        if (king == null) {
            return false;
        }

        return state.board().pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == color.opposite())
                .anyMatch(entry -> canAttack(
                        state.board(), entry.getKey(), king, entry.getValue()));
    }

    private boolean isInCheck(Board board, Color color) {
        Position king = findKing(board, color);
        if (king == null) {
            return false;
        }

        return board.pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == color.opposite())
                .anyMatch(entry -> canAttack(board, entry.getKey(), king, entry.getValue()));
    }

    @Override
    public ApplyResult apply(GameState state, Move move) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(move, "move");
        MoveValidationResult validation = validate(state, move);
        if (!validation.valid()) {
            return new ApplyResult(state, validation, List.of());
        }

        Piece source = state.board().pieceAt(move.source()).orElseThrow();
        Piece destination = state.board().pieceAt(move.destination()).orElse(null);
        Piece movedPiece = source;
        HiddenPiecePool redPool = state.redHiddenPool();
        HiddenPiecePool blackPool = state.blackHiddenPool();
        List<GameEvent> events = new ArrayList<>();

        if (!source.visible()) {
            HiddenPiecePool pool = state.hiddenPool(source.owner());
            PieceType revealedType = draw(pool);
            movedPiece = source.revealAs(revealedType);
            if (source.owner() == Color.RED) {
                redPool = redPool.remove(revealedType);
            } else {
                blackPool = blackPool.remove(revealedType);
            }
            events.add(new GameEvent.PieceRevealed(revealedType));
        }

        Board nextBoard = state.board().move(move.source(), move.destination(), movedPiece);
        int nextNoCaptureHalfMoves =
                destination == null ? state.noCaptureHalfMoves() + 1 : 0;
        Color nextTurn = state.currentTurn().opposite();
        GameStatus nextStatus = winnerAfterCapture(source.owner(), destination);
        boolean givesCheck = isInCheck(nextBoard, source.owner().opposite());
        boolean givesChase = !givesCheck && attacksNonKingEnemy(
                nextBoard, move.destination(), movedPiece);
        Repetition check = updateRepetition(
                state.consecutiveCheckOwner(),
                state.consecutiveCheckCount(),
                source.owner(),
                givesCheck);
        Repetition chase = updateRepetition(
                state.consecutiveChaseOwner(),
                state.consecutiveChaseCount(),
                source.owner(),
                givesChase);
        if (nextStatus == GameStatus.PLAYING
                && check.owner() == source.owner()
                && check.count() >= REPETITION_LIMIT) {
            nextStatus = source.owner() == Color.RED
                    ? GameStatus.BLACK_WIN
                    : GameStatus.RED_WIN;
        }
        if (nextStatus == GameStatus.PLAYING
                && chase.owner() == source.owner()
                && chase.count() >= REPETITION_LIMIT) {
            nextStatus = movedPiece.movementType() == PieceType.PAWN
                    ? GameStatus.DRAW
                    : source.owner() == Color.RED
                            ? GameStatus.BLACK_WIN
                            : GameStatus.RED_WIN;
        }
        if (nextStatus == GameStatus.PLAYING
                && nextNoCaptureHalfMoves >= DRAW_HALF_MOVE_LIMIT) {
            nextStatus = GameStatus.DRAW;
        }

        GameState next = new GameState(
                nextBoard,
                nextTurn,
                nextNoCaptureHalfMoves,
                check.count(),
                chase.count(),
                check.owner(),
                chase.owner(),
                System.currentTimeMillis(),
                nextStatus,
                redPool,
                blackPool);

        if (nextStatus == GameStatus.PLAYING && legalMoves(next).isEmpty()) {
            nextStatus = source.owner() == Color.RED
                    ? GameStatus.RED_WIN
                    : GameStatus.BLACK_WIN;
            next = withStatus(next, nextStatus);
        }

        if (nextStatus == GameStatus.PLAYING) {
            events.add(new GameEvent.TurnChanged(nextTurn));
        } else {
            events.add(new GameEvent.GameEnded(nextStatus));
        }
        return new ApplyResult(next, MoveValidationResult.accepted(), events);
    }

    private MoveValidationResult validate(GameState state, Move move) {
        if (state.status() != GameStatus.PLAYING) {
            return MoveValidationResult.rejected(
                    MoveError.GAME_NOT_ACTIVE, "对局已经结束");
        }
        if (move.source().equals(move.destination())) {
            return MoveValidationResult.rejected(
                    MoveError.FLIP_IN_PLACE_FORBIDDEN, "不允许原地翻子");
        }

        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null) {
            return MoveValidationResult.rejected(MoveError.SOURCE_EMPTY, "起点没有棋子");
        }
        if (source.owner() != state.currentTurn()) {
            return MoveValidationResult.rejected(MoveError.NOT_YOUR_PIECE, "不能移动对方棋子");
        }
        Piece destination = state.board().pieceAt(move.destination()).orElse(null);
        if (destination != null && destination.owner() == source.owner()) {
            return MoveValidationResult.rejected(
                    MoveError.FRIENDLY_DESTINATION, "终点已有己方棋子");
        }
        if (!isLegalMovement(state.board(), move.source(), move.destination())) {
            return MoveValidationResult.rejected(
                    MoveError.ILLEGAL_PIECE_MOVEMENT, "棋子不能这样移动");
        }
        return MoveValidationResult.accepted();
    }

    private boolean isLegalMovement(Board board, Position source, Position destination) {
        if (source.equals(destination)) {
            return false;
        }
        Piece piece = board.pieceAt(source).orElse(null);
        if (piece == null) {
            return false;
        }
        Piece target = board.pieceAt(destination).orElse(null);
        if (target != null && target.owner() == piece.owner()) {
            return false;
        }

        if (!canAttack(board, source, destination, piece)) {
            return false;
        }

        Board moved = board.move(source, destination, piece);
        return !kingsFace(moved);
    }

    private boolean canAttack(
            Board board, Position source, Position destination, Piece piece) {
        Piece target = board.pieceAt(destination).orElse(null);
        if (target != null && target.owner() == piece.owner()) {
            return false;
        }

        int dx = destination.file() - source.file();
        int dy = destination.rank() - source.rank();
        return switch (piece.movementType()) {
            case ROOK -> isStraight(dx, dy) && countBetween(board, source, destination) == 0;
            case KNIGHT -> isKnight(board, source, dx, dy);
            case CANNON -> isCannon(board, source, destination, target);
            case PAWN -> isPawn(piece.owner(), source, dx, dy);
            case KING -> isKing(piece.owner(), destination, dx, dy);
            case GUARD -> Math.abs(dx) == 1 && Math.abs(dy) == 1;
            case BISHOP -> isBishop(board, source, dx, dy);
        };
    }

    private boolean isKnight(Board board, Position source, int dx, int dy) {
        if (!((Math.abs(dx) == 1 && Math.abs(dy) == 2)
                || (Math.abs(dx) == 2 && Math.abs(dy) == 1))) {
            return false;
        }
        Position leg = Math.abs(dx) == 2
                ? new Position(source.file() + Integer.signum(dx), source.rank())
                : new Position(source.file(), source.rank() + Integer.signum(dy));
        return board.pieceAt(leg).isEmpty();
    }

    private boolean isCannon(
            Board board, Position source, Position destination, Piece target) {
        int dx = destination.file() - source.file();
        int dy = destination.rank() - source.rank();
        if (!isStraight(dx, dy)) {
            return false;
        }
        int between = countBetween(board, source, destination);
        return target == null ? between == 0 : between == 1;
    }

    private boolean isPawn(Color owner, Position source, int dx, int dy) {
        int forward = owner == Color.RED ? 1 : -1;
        if (dx == 0 && dy == forward) {
            return true;
        }
        boolean crossedRiver = owner == Color.RED ? source.rank() >= 5 : source.rank() <= 4;
        return crossedRiver && Math.abs(dx) == 1 && dy == 0;
    }

    private boolean isKing(Color owner, Position destination, int dx, int dy) {
        if (Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        int minimumRank = owner == Color.RED ? 0 : 7;
        int maximumRank = owner == Color.RED ? 2 : 9;
        return destination.file() >= 3
                && destination.file() <= 5
                && destination.rank() >= minimumRank
                && destination.rank() <= maximumRank;
    }

    private boolean isBishop(Board board, Position source, int dx, int dy) {
        if (Math.abs(dx) != 2 || Math.abs(dy) != 2) {
            return false;
        }
        Position eye = new Position(
                source.file() + dx / 2,
                source.rank() + dy / 2);
        return board.pieceAt(eye).isEmpty();
    }

    private int countBetween(Board board, Position source, Position destination) {
        int stepFile = Integer.signum(destination.file() - source.file());
        int stepRank = Integer.signum(destination.rank() - source.rank());
        int file = source.file() + stepFile;
        int rank = source.rank() + stepRank;
        int count = 0;
        while (file != destination.file() || rank != destination.rank()) {
            if (board.pieceAt(new Position(file, rank)).isPresent()) {
                count++;
            }
            file += stepFile;
            rank += stepRank;
        }
        return count;
    }

    private boolean kingsFace(Board board) {
        Position redKing = findKing(board, Color.RED);
        Position blackKing = findKing(board, Color.BLACK);
        if (redKing == null || blackKing == null || redKing.file() != blackKing.file()) {
            return false;
        }
        return countBetween(board, redKing, blackKing) == 0;
    }

    private Position findKing(Board board, Color color) {
        return board.pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == color)
                .filter(entry -> entry.getValue().visible())
                .filter(entry -> entry.getValue().actualType() == PieceType.KING)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private PieceType draw(HiddenPiecePool pool) {
        int selected = random.nextInt(pool.total());
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (selected < count) {
                return type;
            }
            selected -= count;
        }
        throw new IllegalStateException("暗子池状态无效");
    }

    private GameStatus winnerAfterCapture(Color mover, Piece captured) {
        if (captured == null
                || !captured.visible()
                || captured.actualType() != PieceType.KING) {
            return GameStatus.PLAYING;
        }
        return mover == Color.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
    }

    private boolean attacksNonKingEnemy(Board board, Position source, Piece piece) {
        return board.pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == piece.owner().opposite())
                .filter(entry -> entry.getValue().movementType() != PieceType.KING)
                .anyMatch(entry -> canAttack(board, source, entry.getKey(), piece));
    }

    private Repetition updateRepetition(
            Color previousOwner, int previousCount, Color mover, boolean active) {
        if (active) {
            int count = previousOwner == mover ? previousCount + 1 : 1;
            return new Repetition(mover, count);
        }
        if (previousOwner == mover) {
            return new Repetition(null, 0);
        }
        return new Repetition(previousOwner, previousCount);
    }

    private boolean isStraight(int dx, int dy) {
        return (dx == 0) != (dy == 0);
    }

    private GameState withStatus(GameState state, GameStatus status) {
        return new GameState(
                state.board(),
                state.currentTurn(),
                state.noCaptureHalfMoves(),
                state.consecutiveCheckCount(),
                state.consecutiveChaseCount(),
                state.consecutiveCheckOwner(),
                state.consecutiveChaseOwner(),
                state.turnStartedAt(),
                status,
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    private record Repetition(Color owner, int count) {
    }
}
