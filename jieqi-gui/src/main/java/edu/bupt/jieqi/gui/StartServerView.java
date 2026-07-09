package edu.bupt.jieqi.gui;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class StartServerView extends BorderPane {
    private final ServerControl serverControl;
    private final TextField portField = new TextField("8887");
    private final Button toggleButton = new Button("启动服务器");
    private final Button stopButton = new Button("停止服务器");
    private final Label statusLabel = new Label("服务器未启动");
    private final Label localAddressLabel = new Label("本机地址：未启动");
    private final Label lanAddressLabel = new Label("局域网地址：未启动");
    private final TextArea logArea = new TextArea();

    StartServerView(Runnable backAction, ServerControl serverControl) {
        this.serverControl = serverControl;
        this.serverControl.setLogListener(this::appendLog);

        setPadding(new Insets(18));
        setTop(header(backAction));
        setLeft(controlPanel());
        setCenter(instructionsPanel());
        setRight(logPanel());
        BorderPane.setMargin(getCenter(), new Insets(16));
        getStylesheets().add(
                StartServerView.class.getResource("/edu/bupt/jieqi/gui/app.css").toExternalForm());
        refresh();
    }

    void shutdown() {
        try {
            serverControl.stop();
        } catch (Exception exception) {
            appendLog("停止服务器失败：" + safeMessage(exception));
        }
    }

    private HBox header(Runnable backAction) {
        Button back = new Button("返回");
        back.setOnAction(event -> backAction.run());
        Label title = new Label("启动对弈服务器");
        title.getStyleClass().add("section-title");
        HBox header = new HBox(14, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox controlPanel() {
        portField.setPromptText("请输入端口");

        toggleButton.setMaxWidth(Double.MAX_VALUE);
        toggleButton.setOnAction(event -> startServer());

        stopButton.setMaxWidth(Double.MAX_VALUE);
        stopButton.setOnAction(event -> stopServer());

        VBox panel = new VBox(10,
                new Label("服务器控制"),
                new Label("监听端口"),
                portField,
                toggleButton,
                stopButton,
                statusLabel,
                localAddressLabel,
                lanAddressLabel,
                new Label("说明："),
                new Label("1. 启动后，本机客户端可直接连接本机地址。"),
                new Label("2. 其他电脑请连接局域网地址。"),
                new Label("3. 打包后的桌面程序也可直接使用该内嵌服务器。"));
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(280);
        return panel;
    }

    private VBox instructionsPanel() {
        Label title = new Label("联机使用说明");
        title.getStyleClass().add("section-title");

        Label steps = new Label(String.join("\n",
                "1. 点击左侧“启动服务器”。",
                "2. 在两台客户端的“联网对弈与人工智能比赛”页面中填写服务器地址。",
                "3. 本机测试使用 ws://127.0.0.1:端口。",
                "4. 局域网测试使用右侧显示的 ws://局域网IP:端口。",
                "5. 连接后可直接快速匹配或邀请指定玩家。"));
        steps.setWrapText(true);

        VBox addressBox = new VBox(10,
                buildAddressCard("本机调试地址", localAddressLabel),
                buildAddressCard("局域网联机地址", lanAddressLabel));
        VBox.setVgrow(addressBox, Priority.ALWAYS);

        VBox panel = new VBox(18, title, steps, addressBox);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private VBox buildAddressCard(String title, Label content) {
        content.setWrapText(true);
        VBox card = new VBox(8, new Label(title), content);
        card.getStyleClass().add("panel");
        return card;
    }

    private VBox logPanel() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setFocusTraversable(false);

        Button clearButton = new Button("清空日志");
        clearButton.setOnAction(event -> logArea.clear());

        VBox panel = new VBox(10, new Label("服务器日志"), logArea, clearButton);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(340);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return panel;
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException exception) {
            statusLabel.setText("端口无效：请输入数字");
            return;
        }
        if (port < 1 || port > 65535) {
            statusLabel.setText("端口无效：范围应为 1-65535");
            return;
        }
        try {
            serverControl.start(port);
            appendLog("已请求启动服务器，端口 " + port);
        } catch (Exception exception) {
            statusLabel.setText("启动失败：" + safeMessage(exception));
            appendLog("启动服务器失败：" + safeMessage(exception));
        }
        refresh();
    }

    private void stopServer() {
        try {
            serverControl.stop();
            appendLog("服务器已停止");
        } catch (Exception exception) {
            statusLabel.setText("停止失败：" + safeMessage(exception));
            appendLog("停止服务器失败：" + safeMessage(exception));
        }
        refresh();
    }

    private void refresh() {
        boolean running = serverControl.isRunning();
        portField.setDisable(running);
        toggleButton.setDisable(running);
        stopButton.setDisable(!running);
        if (running) {
            int port = serverControl.port();
            statusLabel.setText("服务器运行中，监听端口 " + port);
            localAddressLabel.setText("ws://127.0.0.1:" + port);
            lanAddressLabel.setText(String.join("\n", lanAddresses(port)));
        } else {
            statusLabel.setText("服务器未启动");
            localAddressLabel.setText("本机地址：未启动");
            lanAddressLabel.setText("局域网地址：未启动");
        }
    }

    private List<String> lanAddresses(int port) {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = network.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        addresses.add(network.getDisplayName() + "：ws://" + address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException exception) {
            addresses.add("无法读取局域网地址：" + safeMessage(exception));
        }
        if (addresses.isEmpty()) {
            addresses.add("未检测到可用局域网 IPv4 地址");
        }
        return addresses;
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            if (!logArea.getText().isEmpty()) {
                logArea.appendText(System.lineSeparator());
            }
            logArea.appendText(message);
            refresh();
        });
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
