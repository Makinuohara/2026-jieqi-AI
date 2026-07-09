# Jieqi Arena

北京邮电大学 2026 Java 面向对象大作业“揭棋”的多人协作框架。

## 项目架构

本项目是七模块 Maven 工程：

- `jieqi-model`：棋盘、棋子、走法、对局状态和玩家视图模型。
- `jieqi-rules`：规则引擎、合法性判断、翻子事件和终局裁决。
- `jieqi-ai`：统一 AI 接口、随机 AI、贪心 AI 和 Expectiminimax 搜索 AI。
- `jieqi-protocol`：联网 JSON 消息、协议编解码和 1 KiB 文本帧限制。
- `jieqi-server`：WebSocket 对弈服务器。
- `jieqi-gui`：JavaFX 本地界面、本地 AI 对战、棋谱保存/复盘和批量 AI 实验。
- `jieqi-app`：统一启动入口，负责转发 GUI、服务器和批量实验启动参数。

根目录保留 Maven Wrapper、Docker 配置和一键启动入口；开发说明、协议约定、
任务拆分和迭代记录放在 `docs/` 下。作业原始资料、展示页和报告图片统一放在
`resources/` 下，脚本主体放在 `scripts/` 下。

## 目录说明

- `jieqi-*`：Maven 业务模块。
- `docs/`：架构说明、协议约定、任务拆分和迭代记录。
- `resources/assignment/`：作业原始资料和老师答疑文件。
- `resources/presentations/`：验收展示页面。
- `resources/report-images/`：报告截图素材。
- `scripts/`：GUI 和服务器启动脚本主体。
- `run-gui.*`、`run-server.*`：根目录兼容入口，转发到 `scripts/`。

## 项目目标

项目最终计划支持：

- 本地真人对 AI。
- 本地 AI 对 AI。
- AI 作为客户端连接统一服务器比赛。
- 两个客户端通过服务器完成正常对弈。
- JavaFX 图形界面。
- Docker 部署对弈服务器。

规定优先级：

1. `resources/assignment/问题回答.txt`
2. `resources/assignment/揭棋规则与协议待确认问题的老师解答.txt`
3. `resources/assignment/2026大作业公共接口.docx`
4. `resources/assignment/2026大作业——揭棋.docx`
5. `resources/assignment/Unvei接口文档- 张恒基.pdf` 中不冲突的参考内容

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
  置换表局面缓存、战术走法排序、根节点迭代加深和安静搜索。
- AI 会优先保护己方将帅，被将军时会优先选择躲将、吃掉威胁棋子或阻挡威胁的走法。
- Expectiminimax AI 会记录对局中已经公开见过的明子，用于修正后续暗子概率估计，
  但不会读取暗子的真实隐藏身份。
- 提供中文 JavaFX 首屏和四种模式入口。
- “真人对人工智能”已经接通真实棋盘，真人执红，搜索 AI 执黑。
- “本地人工智能对弈”已经接通真实棋盘，默认由红方贪心 AI 对战黑方随机 AI，
  并支持分别选择红黑双方使用随机、贪心或搜索 AI。
- 支持选子、合法落点高亮、走子、AI 后台回应、认输和重新开始。
- 真人对 AI 模式使用响应优先的搜索预算，普通局面保持较快响应，被将、能立即取胜或
  残局时自动临时加深搜索；每层完整搜索后会保留当前最优着法，并用本层结果重排下一层
  根节点候选，降低限时思考时因超时退回贪心兜底的概率。
- 支持本地 AI 对 AI 的开始、暂停、继续、单步和速度调整。
- 支持本地对局结束后保存棋谱、查看复盘和打开已保存棋谱。
- 支持本地 AI 对 AI 批量实验，统计不同 AI 组合的胜率、平均步数、平均耗时、异常局数
  和非法走法局数。
- GUI 棋盘已改为接近传统象棋盘的绘制方式，并统一使用圆形棋子样式。
- 支持区分空位落点提示、吃子落点提示和上一步走子的起点/终点高亮。
- 左侧信息区会持续显示当前将军状态；被将军一方的将/帅会在棋盘上高亮提示。
- 初始将帅直接明牌；其他暗子移动后会在落点显示实际翻出棋名。
- 红方使用“帅、仕、相、兵”，黑方使用“将、士、象、卒”。
- 建立 WebSocket 服务器和公共 JSON 编解码。
- 支持 `ping`、临时玩家身份和未知可选消息兼容。
- 支持联网玩家大厅、玩家列表广播、快速匹配、邀请/接受匹配、房间创建、服务器裁决走法和
  双 GUI 状态同步。
- 对大于或等于 8192 字节的文本帧使用关闭码 `1009` 拒绝。
- 提供服务器 Dockerfile 和 Docker Compose 配置。
- 当前共有 89 项自动化测试，覆盖模型、规则、AI、协议、服务器以及本地和联网 GUI 对局，
  可以通过 `./mvnw test` 运行。

## 需要完善

当前已经可以完成本地真人对搜索 AI、本地 AI 对 AI，以及基础联网真人对真人揭棋对局。
其他模式仍在继续开发。主要缺少：

- 联网对局重连、断线裁决、观战和更完整的房间管理。
- AI 联网客户端、联网棋谱保存及复盘。
- 跨电脑和跨小组 AI 联调。

当前可运行本地真人对搜索 AI、本地 AI 对 AI 基础自动对局，以及通过服务器匹配的
联网真人对真人对局。后续重点是完善联网计时、稳定性和 AI 联网接入。
详细任务见 [`docs/开发任务.md`](docs/开发任务.md)。

## 本地真人对搜索人工智能

Windows 一键运行：

```powershell
.\run-gui.cmd
```

macOS 一键运行：

```bash
./run-gui.sh
```

也可以直接运行脚本主体：

```bash
./scripts/run-gui.sh
```

进入“真人对人工智能”后：

1. 真人执红先行。
2. 点击红方棋子，绿色棋位表示合法落点。
3. 点击绿色棋位完成走子。
4. 搜索人工智能会在后台自动走黑方。
5. 吃掉对方将帅、认输或点击“重新开始”可以结束或重置当前对局。
6. 对局结束后可以点击“查看复盘”显示完整走法，或点击“保存棋谱”导出文本棋谱。

当前版本允许玩家不应将，但仍禁止走出将帅直接照面的局面。连续 80 个半回合无吃子
会自动判和；连续 6 次长将、长捉及兵卒例外也已纳入规则引擎。

## 本地人工智能对弈

Windows 一键运行：

```powershell
.\run-gui.cmd
```

macOS 一键运行：

```bash
./run-gui.sh
```

进入“本地人工智能对弈”后：

1. 红方默认使用贪心 AI，黑方默认使用随机 AI。
2. 可以分别选择红方和黑方使用随机 AI、贪心 AI 或搜索 AI。
3. 点击“开始”后，对局才会自动推进。
4. 可以使用“暂停 / 继续”“单步”“重新开始”观察对局过程。
5. 可以通过“播放速度”切换自动对弈节奏。
6. 左侧会持续显示当前将军状态，棋盘会保留上一步走子的高亮提示。
7. 对局结束后可以点击“查看复盘”显示完整走法，或点击“保存棋谱”导出文本棋谱。

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

## 联网真人对真人对弈

先在作为服务器的一台电脑上启动对弈服务器：

Windows：

```powershell
.\run-server.cmd
```

macOS：

```bash
./run-server.sh
```

也可以直接运行脚本主体：

```bash
./scripts/run-server.sh
```

服务器默认监听：

```text
ws://localhost:8887
```

同一台电脑上双开 GUI 测试时：

1. 保持服务器窗口运行。
2. 分别打开两个终端，各执行一次：

Windows：

```powershell
.\run-gui.cmd
```

macOS：

```bash
./run-gui.sh
```

3. 两个 GUI 都进入“联网对弈与人工智能比赛”。
4. 服务器地址都填写 `ws://127.0.0.1:8887`，点击“连接”。
5. 连接成功后会自动进入联机房间，左侧会同步显示房间内玩家及状态。
6. 双方可以都点击“快速匹配”，也可以选中空闲玩家后点击“邀请选中玩家”。
7. 匹配或邀请成功后，双方自动准备并进入对局。

不同电脑联机时：

1. 服务器电脑先运行对应系统的服务器启动脚本。
2. 在服务器电脑上查看局域网 IP。

Windows：

```powershell
ipconfig
```

macOS：

```bash
ipconfig getifaddr en0
```

3. 其他电脑的 GUI 服务器地址填写：

```text
ws://服务器电脑IP:8887
```

例如：

```text
ws://192.168.1.23:8887
```

4. 点击“连接”后进入同一个联机房间，再使用快速匹配或邀请开始对局。

如果其他小组使用网页或其他语言客户端，也可以连接同一个服务器；只要客户端通过
WebSocket 按公共 JSON 协议发送 `startMatch`、`Ready`、`move`、`Resign`、`ping`
等消息，并处理 `matchSuccess`、`gameStart`、`moveResult`、`timeout`、`gameOver`
等返回消息即可。房间玩家列表和邀请属于本项目扩展消息，网页端如果不接入这些扩展，
直接使用 `startMatch` 快速匹配最稳。

## 常用命令

Windows：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd install
.\run-gui.cmd
.\run-server.cmd
.\scripts\run-gui.cmd
.\scripts\run-server.cmd
docker compose up --build
```

macOS：

```bash
./mvnw test
./mvnw package
./mvnw install
./run-gui.sh
./run-server.sh
./scripts/run-gui.sh
./scripts/run-server.sh
docker compose up --build
```

首次直接运行 `jieqi-gui` 子模块时，如果依赖模块尚未安装到本机 Maven 仓库，会出现
`jieqi-ai` 或 `jieqi-protocol` 无法解析的错误。Windows 推荐使用 `.\run-gui.cmd`，
macOS 推荐使用 `./run-gui.sh`；脚本会先构建并安装 GUI 依赖，再启动 JavaFX。

Windows 手动执行：

```powershell
.\mvnw.cmd -pl jieqi-gui -am install -DskipTests
.\mvnw.cmd -f .\jieqi-gui\pom.xml javafx:run
```

macOS 手动执行：

```bash
./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
```

服务器默认监听 `ws://localhost:8887`。协议文本帧必须小于 8192 字节。

更多说明见：

- [`docs/架构说明.md`](docs/架构说明.md)
- [`docs/开发任务.md`](docs/开发任务.md)
- [`docs/测试成果.md`](docs/测试成果.md)
- [`docs/协议约定.md`](docs/协议约定.md)
