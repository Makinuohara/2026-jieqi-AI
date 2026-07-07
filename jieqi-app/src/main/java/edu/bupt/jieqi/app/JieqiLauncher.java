package edu.bupt.jieqi.app;

import edu.bupt.jieqi.gui.JieqiApplication;
import edu.bupt.jieqi.gui.LocalAiVsAiExperiment;
import edu.bupt.jieqi.server.ServerMain;
import java.util.Arrays;

public final class JieqiLauncher {
    private JieqiLauncher() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            ServerMain.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && "ai-experiment".equalsIgnoreCase(args[0])) {
            LocalAiVsAiExperiment.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        JieqiApplication.launchApp(args);
    }
}
