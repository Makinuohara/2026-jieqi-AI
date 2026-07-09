package edu.bupt.jieqi.app;

import edu.bupt.jieqi.gui.ServerControl;
import edu.bupt.jieqi.server.JieqiWebSocketServer;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class EmbeddedServerControl implements ServerControl {
    private Consumer<String> logListener = message -> {
    };
    private JieqiWebSocketServer server;
    private int port = 8887;

    @Override
    public synchronized void setLogListener(Consumer<String> listener) {
        logListener = Objects.requireNonNull(listener);
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null;
    }

    @Override
    public synchronized int port() {
        return port;
    }

    @Override
    public synchronized void start(int port) throws Exception {
        if (server != null) {
            throw new IllegalStateException("服务器已经在运行");
        }
        this.port = port;
        CountDownLatch started = new CountDownLatch(1);
        JieqiWebSocketServer created = new JieqiWebSocketServer(
                new InetSocketAddress("0.0.0.0", port),
                message -> {
                    logListener.accept(message);
                    if (message.contains("已监听")) {
                        started.countDown();
                    }
                },
                message -> {
                    logListener.accept(message);
                    started.countDown();
                });
        try {
            created.start();
            if (!started.await(5, TimeUnit.SECONDS)) {
                created.stop(1000);
                throw new IllegalStateException("服务器启动超时");
            }
            server = created;
        } catch (Exception exception) {
            safeStop(created);
            if (exception instanceof BindException) {
                throw new IllegalStateException("端口 " + port + " 已被占用", exception);
            }
            String message = exception.getMessage();
            if (message != null && message.contains("Address already in use")) {
                throw new IllegalStateException("端口 " + port + " 已被占用", exception);
            }
            throw exception;
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (server == null) {
            return;
        }
        JieqiWebSocketServer running = server;
        server = null;
        running.stop(1000);
        logListener.accept("揭棋服务器已停止监听");
    }

    private void safeStop(JieqiWebSocketServer created) {
        try {
            created.stop(1000);
        } catch (Exception ignored) {
        }
    }
}
