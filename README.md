# Jieqi Arena

北京邮电大学 2026 Java 面向对象大作业“揭棋”的多人协作框架。

## 项目目标

项目最终计划支持：

- 本地真人对 AI。
- 本地 AI 对 AI。
- AI 作为客户端连接统一服务器比赛。
- 两个客户端通过服务器完成正常对弈。
- JavaFX 图形界面。
- Docker 部署对弈服务器。

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

## 已经实现

当前仓库已经具备可编译、可测试和可继续扩展的主体框架：

- 建立七模块 Maven 项目并统一使用 Java 21。
- 提供 Maven Wrapper，不要求电脑预先全局安装 Maven。
- 建立棋盘、坐标、棋子、走法、对局状态和玩家视图模型。
- 创建初始棋盘和双方暗子剩余类型池。
- `PlayerView` 会隐藏暗子的真实身份，避免 AI 读取完整信息。
- 建立 `GameEngine.apply` 统一棋局推进接口。
- 完成空起点、错误阵营、己方棋子占位和原地翻子等基础拒绝逻辑。
- 建立 `Agent.chooseMove` 统一 AI 接口。
- 提供随机 AI 和基础贪心 AI。
- 建立 Expectiminimax 类和可替换评估函数接口。
- 提供 JavaFX 首屏和四种模式入口的界面外壳。
- 建立 WebSocket 服务器和公共 JSON 编解码。
- 支持 `ping`、临时登录和未知可选消息兼容。
- 对大于或等于 1024 字节的文本帧使用关闭码 `1009` 拒绝。
- 提供服务器 Dockerfile 和 Docker Compose 配置。
- 当前单元测试共 11 项，可以通过 `.\mvnw.cmd test` 运行。

## 需要完善

当前仍是项目框架，尚不能完成一整盘真实揭棋对局。主要缺少：

- 完整棋子移动、吃子和合法走法生成。
- 马腿、象眼、炮架、将帅照面以及士象强化规则。
- 暗子首次移动或吃子后的服务端随机翻子。
- 将军、吃将、胜负、无子可走和认输判断。
- 80 个半回合无吃子判和。
- 连续 6 次长将、长捉及兵卒例外。
- 完整贪心评估和 Expectiminimax 概率搜索。
- 随机 AI 对随机 AI 的完整自动对局循环。
- JavaFX 棋盘点击、走法显示和真人对 AI。
- AI 对 AI 的暂停、单步和速度调整。
- AI 后台搜索线程，避免阻塞 JavaFX 界面。
- 服务器匹配、Ready、房间、计时和最终裁决。
- 合法结果广播、非法走法单独回复和暗子信息脱敏。
- AI 联网客户端、棋谱保存及复盘。
- 两个客户端通过服务器完成整局对弈。
- 跨电脑和跨小组 AI 联调。

推荐先完成规则引擎和合法走法生成，再依次接通本地 AI 对局、图形界面和联网服务器。
详细任务见 [`docs/开发任务.md`](docs/开发任务.md)。

## 常用命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd install
.\run-gui.cmd
java -jar .\jieqi-server\target\jieqi-server-0.1.0-SNAPSHOT-all.jar
docker compose up --build
```

首次直接运行 `jieqi-gui` 子模块时，如果依赖模块尚未安装到本机 Maven 仓库，会出现
`jieqi-ai` 或 `jieqi-protocol` 无法解析的错误。推荐使用 `.\run-gui.cmd`，脚本会先
构建并安装 GUI 依赖，再启动 JavaFX。

也可以手动执行：

```powershell
.\mvnw.cmd -pl jieqi-gui -am install -DskipTests
.\mvnw.cmd -f .\jieqi-gui\pom.xml javafx:run
```

服务器默认监听 `ws://localhost:8887`。协议文本帧必须小于 1024 字节。

更多说明见：

- [`docs/架构说明.md`](docs/架构说明.md)
- [`docs/开发任务.md`](docs/开发任务.md)
- [`docs/协议约定.md`](docs/协议约定.md)
