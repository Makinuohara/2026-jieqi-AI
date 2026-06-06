# Team Tasks

| Role | Contribution | Ownership |
|---|---:|---|
| A Architecture and integration | 25% | Parent build, public interfaces, application wiring, reviews |
| B Rules engine | 15% | Movement, blocking, check, mate, stalemate and rule tests |
| C AI search | 15% | Agent search, Expectiminimax, budgets and performance |
| D Evaluation and experiments | 15% | Greedy evaluation, hidden-piece expectation and tournaments |
| E JavaFX client | 15% | Board UI, local modes, replay and background AI execution |
| F Protocol and server | 15% | JSON compatibility, rooms, clocks, records and Docker |

## Integration order

1. Model and interfaces
2. Rules engine
3. Random AI versus random AI
4. JavaFX human versus AI
5. Greedy and Expectiminimax
6. WebSocket AI versus AI
7. Docker and cross-team testing

Each owner adds tests and documentation in the same pull request as the feature.
Use feature branches and review before merging.

