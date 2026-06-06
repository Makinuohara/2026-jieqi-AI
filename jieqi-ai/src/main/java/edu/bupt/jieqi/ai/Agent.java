package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;

public interface Agent {
    Move chooseMove(PlayerView view, SearchBudget budget);

    String name();
}

