# Jieqi Arena

北京邮电大学 2026 Java 面向对象大作业“揭棋”的多人协作框架。

## 当前范围

本仓库当前交付的是可编译、可测试、可分工的主体框架，而不是完整棋力成品。规则、
AI、JavaFX 和 WebSocket 均有稳定模块边界，队员可以并行实现。

规定优先级：

1. `问题回答.txt`
2. `2026大作业公共接口.docx`
3. `2026大作业——揭棋.docx`
4. Unveil v3.1 PDF 中不冲突的参考内容

## 环境

- JDK 21
- Maven Wrapper
- JavaFX 21
- Java-WebSocket 1.5.7
- Gson 2.10.1

## 常用命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd -pl jieqi-gui -am javafx:run
java -jar .\jieqi-server\target\jieqi-server-0.1.0-SNAPSHOT-all.jar
docker compose up --build
```

服务器默认监听 `ws://localhost:8887`。协议文本帧必须小于 1024 字节。

更多说明见：

- `docs/architecture.md`
- `docs/team-tasks.md`
- `docs/protocol-decisions.md`
