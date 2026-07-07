package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.ai.SearchBudget;
import edu.bupt.jieqi.model.GameStatus;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LocalAiVsAiExperiment {
    private static final int DEFAULT_GAMES_PER_MATCHUP = 3;
    private static final int DEFAULT_MAX_HALF_MOVES = 300;
    private static final int DEFAULT_BUDGET_MILLIS = 200;

    private LocalAiVsAiExperiment() {
    }

    public static void main(String[] args) {
        System.out.print(run(parseConfig(args)));
    }

    public static String run(Config config) {
        Map<LocalAiVsAiGame.AiMode, OverallStats> overallStats = new EnumMap<>(LocalAiVsAiGame.AiMode.class);
        for (LocalAiVsAiGame.AiMode mode : config.modes()) {
            overallStats.put(mode, new OverallStats());
        }

        StringBuilder report = new StringBuilder();
        report.append("本地 AI 对 AI 批量实验\n")
                .append("每组局数：").append(config.gamesPerMatchup())
                .append("，最大半步：").append(config.maxHalfMoves())
                .append("，单步预算：").append(config.budgetMillis()).append("ms\n\n")
                .append(String.format("%-14s %-14s %6s %6s %6s %6s %8s %8s %8s%n",
                        "红方AI", "黑方AI", "红胜", "黑胜", "和棋", "异常", "平均步数", "平均耗时", "非法"));

        for (LocalAiVsAiGame.AiMode redMode : config.modes()) {
            for (LocalAiVsAiGame.AiMode blackMode : config.modes()) {
                MatchupStats stats = runMatchup(redMode, blackMode, config);
                overallStats.get(redMode).recordAsRed(stats);
                overallStats.get(blackMode).recordAsBlack(stats);
                report.append(String.format(Locale.ROOT,
                        "%-14s %-14s %6.1f %6.1f %6.1f %6d %8.1f %7.2fs %8d%n",
                        redMode,
                        blackMode,
                        stats.rate(stats.redWins()),
                        stats.rate(stats.blackWins()),
                        stats.rate(stats.draws()),
                        stats.exceptions(),
                        stats.averageHalfMoves(),
                        stats.averageSeconds(),
                        stats.illegalMoves()));
            }
        }
        appendFinalResult(report, overallStats);
        return report.toString();
    }

    private static void appendFinalResult(
            StringBuilder report,
            Map<LocalAiVsAiGame.AiMode, OverallStats> overallStats) {
        LocalAiVsAiGame.AiMode winner = overallStats.entrySet().stream()
                .max((left, right) -> left.getValue().compareTo(right.getValue()))
                .map(Map.Entry::getKey)
                .orElseThrow();

        report.append("\n最终对战结果\n")
                .append("综合第一：").append(winner)
                .append("（胜率 ")
                .append(String.format(Locale.ROOT, "%.1f", overallStats.get(winner).winRate()))
                .append("%）\n\n")
                .append(String.format("%-14s %6s %6s %6s %6s %6s %6s %8s %8s %8s%n",
                        "AI", "总局", "胜", "和", "负", "异常", "非法", "胜率", "未败率", "平均耗时"));

        overallStats.entrySet().stream()
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .forEach(entry -> {
                    OverallStats stats = entry.getValue();
                    report.append(String.format(Locale.ROOT,
                            "%-14s %6d %6d %6d %6d %6d %6d %7.1f%% %7.1f%% %7.2fs%n",
                            entry.getKey(),
                            stats.games(),
                            stats.wins(),
                            stats.draws(),
                            stats.losses(),
                            stats.exceptions(),
                            stats.illegalMoves(),
                            stats.winRate(),
                            stats.nonLossRate(),
                            stats.averageSeconds()));
                });
    }

    private static MatchupStats runMatchup(
            LocalAiVsAiGame.AiMode redMode,
            LocalAiVsAiGame.AiMode blackMode,
            Config config) {
        MatchupStats stats = new MatchupStats();
        SearchBudget budget = new SearchBudget(Duration.ofMillis(config.budgetMillis()), 1);
        for (int gameIndex = 0; gameIndex < config.gamesPerMatchup(); gameIndex++) {
            LocalAiVsAiGame game = new LocalAiVsAiGame(
                    redMode.createAgent(),
                    blackMode.createAgent(),
                    budget);
            long startedAt = System.nanoTime();
            int halfMoves = 0;
            try {
                while (game.state().status() == GameStatus.PLAYING
                        && halfMoves < config.maxHalfMoves()) {
                    if (game.performNextMove().isEmpty()) {
                        break;
                    }
                    halfMoves++;
                }
                stats.record(game.state().status(), halfMoves, System.nanoTime() - startedAt);
            } catch (RuntimeException exception) {
                stats.recordException(halfMoves, System.nanoTime() - startedAt, isIllegalMove(exception));
            }
        }
        return stats;
    }

    private static boolean isIllegalMove(RuntimeException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("非法走法");
    }

    private static Config parseConfig(String[] args) {
        int gamesPerMatchup = args.length > 0 ? parsePositive(args[0], "gamesPerMatchup")
                : DEFAULT_GAMES_PER_MATCHUP;
        int maxHalfMoves = args.length > 1 ? parsePositive(args[1], "maxHalfMoves")
                : DEFAULT_MAX_HALF_MOVES;
        int budgetMillis = args.length > 2 ? parsePositive(args[2], "budgetMillis")
                : DEFAULT_BUDGET_MILLIS;
        return new Config(
                gamesPerMatchup,
                maxHalfMoves,
                budgetMillis,
                Arrays.asList(LocalAiVsAiGame.AiMode.values()));
    }

    private static int parsePositive(String raw, String name) {
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public record Config(
            int gamesPerMatchup,
            int maxHalfMoves,
            int budgetMillis,
            List<LocalAiVsAiGame.AiMode> modes) {
        public Config {
            if (gamesPerMatchup <= 0 || maxHalfMoves <= 0 || budgetMillis <= 0) {
                throw new IllegalArgumentException("experiment values must be positive");
            }
            modes = List.copyOf(modes);
            if (modes.isEmpty()) {
                throw new IllegalArgumentException("at least one AI mode is required");
            }
        }
    }

    private static final class MatchupStats {
        private int games;
        private int redWins;
        private int blackWins;
        private int draws;
        private int exceptions;
        private int illegalMoves;
        private int totalHalfMoves;
        private long totalNanos;

        void record(GameStatus status, int halfMoves, long nanos) {
            games++;
            totalHalfMoves += halfMoves;
            totalNanos += nanos;
            switch (status) {
                case RED_WIN -> redWins++;
                case BLACK_WIN -> blackWins++;
                case DRAW, PLAYING, WAITING -> draws++;
            }
        }

        void recordException(int halfMoves, long nanos, boolean illegalMove) {
            games++;
            exceptions++;
            if (illegalMove) {
                illegalMoves++;
            }
            totalHalfMoves += halfMoves;
            totalNanos += nanos;
        }

        int redWins() {
            return redWins;
        }

        int blackWins() {
            return blackWins;
        }

        int draws() {
            return draws;
        }

        int exceptions() {
            return exceptions;
        }

        int illegalMoves() {
            return illegalMoves;
        }

        double rate(int count) {
            return games == 0 ? 0.0 : count * 100.0 / games;
        }

        double averageHalfMoves() {
            return games == 0 ? 0.0 : (double) totalHalfMoves / games;
        }

        double averageSeconds() {
            return games == 0 ? 0.0 : totalNanos / 1_000_000_000.0 / games;
        }
    }

    private static final class OverallStats {
        private int games;
        private int wins;
        private int draws;
        private int losses;
        private int exceptions;
        private int illegalMoves;
        private int totalHalfMoves;
        private long totalNanos;

        void recordAsRed(MatchupStats stats) {
            games += stats.games;
            wins += stats.redWins;
            draws += stats.draws;
            losses += stats.blackWins;
            exceptions += stats.exceptions;
            illegalMoves += stats.illegalMoves;
            totalHalfMoves += stats.totalHalfMoves;
            totalNanos += stats.totalNanos;
        }

        void recordAsBlack(MatchupStats stats) {
            games += stats.games;
            wins += stats.blackWins;
            draws += stats.draws;
            losses += stats.redWins;
            exceptions += stats.exceptions;
            illegalMoves += stats.illegalMoves;
            totalHalfMoves += stats.totalHalfMoves;
            totalNanos += stats.totalNanos;
        }

        int games() {
            return games;
        }

        int wins() {
            return wins;
        }

        int draws() {
            return draws;
        }

        int losses() {
            return losses;
        }

        int exceptions() {
            return exceptions;
        }

        int illegalMoves() {
            return illegalMoves;
        }

        double winRate() {
            return games == 0 ? 0.0 : wins * 100.0 / games;
        }

        double nonLossRate() {
            return games == 0 ? 0.0 : (wins + draws) * 100.0 / games;
        }

        double averageSeconds() {
            return games == 0 ? 0.0 : totalNanos / 1_000_000_000.0 / games;
        }

        int compareTo(OverallStats other) {
            int byWinRate = Double.compare(winRate(), other.winRate());
            if (byWinRate != 0) {
                return byWinRate;
            }
            int byNonLossRate = Double.compare(nonLossRate(), other.nonLossRate());
            if (byNonLossRate != 0) {
                return byNonLossRate;
            }
            int byIllegalMoves = Integer.compare(other.illegalMoves, illegalMoves);
            if (byIllegalMoves != 0) {
                return byIllegalMoves;
            }
            return Integer.compare(other.exceptions, exceptions);
        }
    }
}
