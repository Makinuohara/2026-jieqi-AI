package edu.bupt.jieqi.gui;

import java.util.function.Consumer;

public interface ServerControl {
    void setLogListener(Consumer<String> listener);

    boolean isRunning();

    int port();

    void start(int port) throws Exception;

    void stop() throws Exception;
}
