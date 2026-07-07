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
2. `揭棋规则与协议待确认问题的老师解答.txt`
3. `2026大作业公共接口.docx`
4. `2026大作业——揭棋.docx`
5. Unveil v3.1 PDF 中不冲突的参考内容

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
- 实现七类棋子的基础移动、吃子和合法走法生成。
- 实现马腿、象眼、炮架、兵卒过河、将帅九宫和照面限制。
- 明士、明象可以过河和离开九宫。
- 暗子按虚拟棋种移动，首次合法移动或吃子后从剩余类型池随机翻开。
- 支持吃掉将帅后立即结束对局。
- 支持连续 80 个半回合无吃子自动判和，任意吃子后计数清零。
- 支持将死或困毙判负，并保持“允许不应将”的作业特殊规则。
- 支持同时检测双方将帅是否受攻击，并显示中文将军提示。
- 实现连续 6 次长将、长捉及兵卒例外（长捉判和）规则。
- 建立 `Agent.chooseMove` 统一 AI 接口。
- 提供随机 AI、保帅型贪心 AI 和 Expectiminimax 搜索 AI；真人对 AI 模式使用
  Expectiminimax 搜索 AI，本地 AI 对 AI 模式可分别选择红黑双方 AI。
- 建立可替换局面评估函数接口，并实现完整贪心评估、暗子期望价值、机动性、
  吃子压力、位置奖励和将帅安全评分。
- 实现 Expectiminimax 概率搜索，支持暗子剩余池推断、翻子概率节点、Alpha-Beta 剪枝、
  置换表局面缓存、战术走法排序和安静搜索。
- AI 会优先保护己方将帅，被将军时会优先选择躲将、吃掉威胁棋子或阻挡威胁的走法。
- Expectiminimax AI 会记录对局中已经公开见过的明子，用于修正后续暗子概率估计，
  但不会读取暗子的真实隐藏身份。
- 提供中文 JavaFX 首屏和四种模式入口。
- “真人对人工智能”已经接通真实棋盘，真人执红，搜索 AI 执黑。
- “本地人工智能对弈”已经接通真实棋盘，默认由红方贪心 AI 对战黑方随机 AI，
  并支持分别选择红黑双方使用随机、贪心或搜索 AI。
- 支持选子、合法落点高亮、走子、AI 后台回应、认输和重新开始。
- 真人对 AI 模式使用响应优先的搜索预算，普通局面保持较快响应，被将、能立即取胜或
  残局时自动临时加深搜索。
- 支持本地 AI 对 AI 的开始、暂停、继续、单步和速度调整。
- 支持本地 AI 对 AI 批量实验，统计不同 AI 组合的胜率、平均步数、平均耗时、异常局数
  和非法走法局数。
- GUI 棋盘已改为接近传统象棋盘的绘制方式，并统一使用圆形棋子样式。
- 支持区分空位落点提示、吃子落点提示和上一步走子的起点/终点高亮。
- 左侧信息区会持续显示当前将军状态；被将军一方的将/帅会在棋盘上高亮提示。
- 初始将帅直接明牌；其他暗子移动后会在落点显示实际翻出棋名。
- 红方使用“帅、仕、相、兵”，黑方使用“将、士、象、卒”。
- 建立 WebSocket 服务器和公共 JSON 编解码。
- 支持 `ping`、临时登录和未知可选消息兼容。
- 对大于或等于 1024 字节的文本帧使用关闭码 `1009` 拒绝。
- 提供服务器 Dockerfile 和 Docker Compose 配置。
- 当前共有 83 项自动化测试，覆盖模型、规则、AI、协议、服务器以及本地 GUI 对局，
  可以通过 `.\mvnw.cmd test` 运行。

## 需要完善

当前已经可以完成本地真人对搜索 AI 的揭棋对局，其他模式仍在继续开发。主要缺少：

- 服务器匹配、Ready、房间、计时和最终裁决。
- 合法结果广播、非法走法单独回复和暗子信息脱敏。
- AI 联网客户端、棋谱保存及复盘。
- 两个客户端通过服务器完成整局对弈。
- 跨电脑和跨小组 AI 联调。

当前可运行本地真人对搜索 AI，以及本地 AI 对 AI 的基础自动对局。后续重点仍是联网对局、
房间裁决和 AI 联网接入。
详细任务见 [`docs/开发任务.md`](docs/开发任务.md)。

## 本地真人对搜索人工智能

运行：

```powershell
.\run-gui.cmd
```

macOS 运行：

```bash
./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
```

进入“真人对人工智能”后：

1. 真人执红先行。
2. 点击红方棋子，绿色棋位表示合法落点。
3. 点击绿色棋位完成走子。
4. 搜索人工智能会在后台自动走黑方。
5. 吃掉对方将帅、认输或点击“重新开始”可以结束或重置当前对局。

当前版本允许玩家不应将，但仍禁止走出将帅直接照面的局面。连续 80 个半回合无吃子
会自动判和；连续 6 次长将、长捉及兵卒例外也已纳入规则引擎。

## 本地人工智能对弈

运行：

```powershell
.\run-gui.cmd
```

macOS 运行：

```bash
./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
```

进入“本地人工智能对弈”后：

1. 红方默认使用贪心 AI，黑方默认使用随机 AI。
2. 可以分别选择红方和黑方使用随机 AI、贪心 AI 或搜索 AI。
3. 点击“开始”后，对局才会自动推进。
4. 可以使用“暂停 / 继续”“单步”“重新开始”观察对局过程。
5. 可以通过“播放速度”切换自动对弈节奏。
6. 左侧会持续显示当前将军状态，棋盘会保留上一步走子的高亮提示。

## 本地 AI 批量实验

运行所有内置 AI 两两组合，每组 3 局，每局最多 300 个半步，单步思考预算 200ms：

```bash
./mvnw -pl jieqi-app -am install -DskipTests
./mvnw -f jieqi-app/pom.xml exec:java -Dexec.mainClass=edu.bupt.jieqi.app.JieqiLauncher -Dexec.args="ai-experiment"
```

也可以指定参数：`ai-experiment 每组局数 最大半步 单步预算毫秒`，例如每组 10 局、
每局最多 400 个半步、单步预算 500ms：

```bash
./mvnw -f jieqi-app/pom.xml exec:java -Dexec.mainClass=edu.bupt.jieqi.app.JieqiLauncher -Dexec.args="ai-experiment 10 400 500"
```

输出会按红方 AI / 黑方 AI 组合列出红胜率、黑胜率、和棋率、异常局数、平均步数、
平均耗时和非法走法局数，末尾会汇总每个 AI 的总成绩并显示综合第一。

## 常用命令

Windows：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd install
.\run-gui.cmd
java -jar .\jieqi-server\target\jieqi-server-0.1.0-SNAPSHOT-all.jar
docker compose up --build
```

macOS：

```bash
./mvnw test
./mvnw package
./mvnw install
./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
java -jar ./jieqi-server/target/jieqi-server-0.1.0-SNAPSHOT-all.jar
docker compose up --build
```

首次直接运行 `jieqi-gui` 子模块时，如果依赖模块尚未安装到本机 Maven 仓库，会出现
`jieqi-ai` 或 `jieqi-protocol` 无法解析的错误。Windows 推荐使用 `.\run-gui.cmd`，
脚本会先构建并安装 GUI 依赖，再启动 JavaFX。

也可以手动执行：

```powershell
.\mvnw.cmd -pl jieqi-gui -am install -DskipTests
.\mvnw.cmd -f .\jieqi-gui\pom.xml javafx:run
```

macOS 手动执行：

```bash
./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
```

服务器默认监听 `ws://localhost:8887`。协议文本帧必须小于 1024 字节。

更多说明见：

- [`docs/架构说明.md`](docs/架构说明.md)
- [`docs/开发任务.md`](docs/开发任务.md)
- [`docs/测试成果.md`](docs/测试成果.md)
- [`docs/协议约定.md`](docs/协议约定.md)
