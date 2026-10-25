# 大肥鱼桌宠接入 Aries（com.example.aries）

对着你仓库 `Arise0404/Arise`（main 分支，547 个文件、15 个 Java、`languageLevel = JAVA_8`、
minSdk 26 / targetSdk 30 / compileSdk 36）核对后写的。你现在有模型没有功能，
而悬浮窗"要么裁模型要么挡操作"——这两件事一起解决。

## 1. 你仓库现状（我核对到的）

- `app/src/main/assets/coopanion/whale/` 里 `model.json`、`figure.json`、`figure.js` 和 480 张贴图都在 ✓ 完整
- **缺渲染层**：没有 `kit/body.js`（动作）和 `kit/rig.js`（WebGL 网格变形器）。`whale/figure.js` 里有一行
  `await import('../kit/rig.js')`，所以只放 whale 目录是跑不起来的——这就是"功能没有"的直接原因
- `MainActivity.togglePet()` 已有悬浮窗权限引导 ✓；`AndroidManifest.xml` 已声明
  `SYSTEM_ALERT_WINDOW`、`FOREGROUND_SERVICE_SPECIAL_USE`、`PetService` 的 `foregroundServiceType="specialUse"` ✓
- 聊天链路是广播：`AIChatService` 发 `ACTION_UPDATE`（带 `EXTRA_CHUNK`）和 `ACTION_FINISH`（带 `EXTRA_TOPIC_ID`/`EXTRA_ERROR`），
  `MainActivity` 里 `aiReceiver` 消费 ✓ 桌宠挂同一个广播即可，不用改你的聊天代码

## 2. 这套文件放哪里

```
app/src/main/assets/coopanion/
├── overlay.html          ← 悬浮层最小宿主（新增）
├── src/widget.js         ← 桌宠组件本体（新增）
├── src/pet-base.mjs      ← 基址解析（新增，widget 不用它，留给你以后换目录）
├── kit/body.js           ← 新增：走跑跳坐睡、被拎甩出、表情调度
├── kit/rig.js            ← 新增：Live2D 式 WebGL2/WebGL1 网格变形器
└── whale/…               ← 你已有，不动

app/src/main/java/com/example/aries/
├── PetScript.java        ← 把 AI 回复解析成"表情 + 分段正文"
├── PetMood.java          ← 模型没写标记时，按文本线索兜一个表情
├── PetTalk.java          ← 戳它要不要开口、开口对模型说什么（含 45 秒冷却）
├── PetAssetServer.java   ← 只绑 127.0.0.1 的资源服务（WebView 要 http 源才认 ES module）
├── FloatingPetWindow.java← 两层窗口管理
└── PetService.java       ← 重写：替换你原来那份（原来那份的 14 个问题见第 8 节）

test/com/example/aries/   ← 两个纯逻辑测试，不进 APK
```

`overlay.html` 里引 `./src/widget.js`，`widget.js` 引 `../kit/body.js` 与 `../whale/figure.js`，
贴图基址是 `new URL('../whale/', import.meta.url)` → `assets/coopanion/whale/`。
我把你 assets 的目录结构原样仿真跑过一遍，引用全部闭合。

## 3. 为什么这样"功能就全有了"

渲染和动作直接用上形象包自己的三层：`kit/rig.js`（网格 + `rot`/`warp` 变形器链）+
`kit/body.js`（54KB 行为：走路、火影跑、跳、坐、睡、被拎起来甩出去摔晕、眨眼沿眼睑线下压、
视差转头、粒子）+ `whale/figure.js`（52KB 画法：后发/刘海/裙摆/尾巴/鲸鳍/呆毛各自的弹簧、说话口型）。

实测规模：**19 个表情 + 18 个动作 = 37 个词**（`PetScript.words()` 就是这个表），
**8 套配色 × 3 套服装**（`deepseek/harness/chatgpt/claude/gemini/qwen/kimi/minimax` × `maid/cape/crew`）。
自己用 Canvas 重写这一套，按我的判断是数周量级，且只能覆盖其中一部分。

## 4. 两层窗口：你的两个症状一起消

| 层 | 尺寸 | 触摸 | 作用 |
|---|---|---|---|
| 画层 render | 全宽，`STRIP_DP = 360` | `FLAG_NOT_TOUCHABLE`（+ `FLAG_HARDWARE_ACCELERATED`，服务里的窗口不然硬件加速，WebGL 起不来） | 只管画。不挡操作，所以能给大，**不会被裁** |
| 触摸层 touch | 贴身体框 +6dp | `FLAG_NOT_TOUCH_MODAL`（框外照样穿透） | 只有身体那么大，**不挡手机**；坐标换算成画层内后转 `petCall('pointer')` |

按你代码里的换算（`PetService.scaleForStrip()`：想要身体 250dp → `S = 250dp / 237`，
`scale = S / (0.42 × min(1.4, 360dp/420dp)) ≈ 5.38`），我在你 assets 的**同构目录**里用 Chrome 实测：

| 量 | 实测值 |
|---|---|
| 画层 | 1080×1080 px（= 360dp @density3） |
| 身体框 | 678 × 783 px ≈ **226dp × 261dp**（目标 250dp 达成） |
| 跳起最高点 | y = 177px ≥ 0 → **没出画层顶** |
| 身体最低点 | y = 1058px ≤ 1080 → **没出画层底** |
| 触摸层跟随 | 约 7 秒内 `setBox` 上报 34 次（≈5 次/秒，节流上限 14 次/秒生效） |
| 事件 | `ready`、`mode`、`grab`、`drop` 都收到（拎起与放下） |
| 帧率 | 18 fps（**软件光栅 swiftshader**；真机走 GPU 会显著高，这条只是下限） |

不裁的通用式子：**画层高 H ≥ 368 × S + 22**（身体 237 单位 + 跳起约 131 单位 + 鞋底留白）。

## 5. AI 回复自动驱动桌宠

`PetService` 自己注册 `ACTION_UPDATE`/`ACTION_FINISH` 广播，按 `topicId` 累积 chunk，
`ACTION_FINISH` 时把整句交给 `PetScript.parse()`，然后按打字速度（70ms/字）排开演。
**你的 MainActivity / AIChatService 一行都不用改。**

标记约定沿用桌宠上游写法：`【词】` 先做表情/动作再打下一段；`<词>` 打到那个位置时做。
中文名和英文 id 都认（`【开心】`→happy、`【鞠躬】`→bow、`nod`→nod），
认不出的词只丢括号、**内容一个字不吞**（`价格【30元】` 仍然念出"30元"），
未闭合的 `<` 当普通文本（不会把 HTML 似的东西误判成动作）。

想让模型稳定带标记，把这段并进你的 system prompt：

```
你可以用标记表达情绪和动作，一次回复里最多 1 个【】和 1 个 <>：
【开心】【眨眼】【喜欢】【害羞】【惊讶】【生气】【难过】【犯困】【思考】【得意】【嘟嘴】【担心】【认真】【慌张】【害怕】【期待】【大哭】【疑惑】【平静】
<点头><摇头><招手><鞠躬><跳舞><转圈><坐下><睡觉><跳><小跳><走走><跑><张望><转身><发抖><扑腾><晕><站起>
写在句子开头或该做动作的位置，不要在代码块里写。
```

### 模型不听话没写标记怎么办

`PetService.react()` 解析完发现整句没有任何【】标记时，会用 `PetMood.pick(raw)` 兜一个表情。
它是**纯关键词启发式**，不是情绪识别：按规则优先级命中（"我好难过想哭"→ sad 而不是 cry，
两个感叹号算 excited，单个感叹号不算），命中不了就返回 null——**宁可不做表情也不乱做**。
`PetMood` 的每条规则都断言过落在形象包那 37 个词内，不会出现 `do()` 静默失败。

## 6. 触摸交互与"戳它就开口" 

戳、摸头、拎起来甩出去这些判定都在 `kit/body.js` 里，Java 侧只负责把坐标送进来：

```java
petWindow.setTouchForwarder((type, x, y) ->
    callJs("pointer", "{\"type\":\"" + type + "\",\"x\":" + (int) x + ",\"y\":" + (int) y + "}"));
```

实测转发的 down+move 会让 `stats().mode` 变成 `drag`，松手落地（仿真跑里已验证）。

**戳它 → 它主动接一句**已经在 `PetService` 里接好了，链路是这样（不用改你的聊天代码）：

```
overlay 报 {kind:"touch", detail:{kind:"poke"}}   ← 嵌套结构，detail.kind 不会盖掉外层 kind
  → PetTalk.allow(now, last, 45s)                  ← 45 秒冷却，免得狂戳刷屏也免得白烧 token
  → PetTalk.pokePrompt(宠物名, 触感种类)            ← 造一句"（桌面场景）主人把小鲤拎起来了…"
  → 追加为 role:user 写进当前话题（TopicStore.update）
  → startForegroundService(AIChatService)          ← 契约就是你 MainActivity 用的 topicId / userMsg
  → 回包走你已有的 ACTION_UPDATE / ACTION_FINISH
  → PetService.react() 让宠物把这句打出来
```

三个要点：

1. **必须先把 user 消息写进话题**——你的 `AIChatService` 是直接从 `TopicStore` 取 `currentTopic.messages`
   当历史的（代码注释也写了"主线程发号施令时已经存过了"），它自己不再追加。
2. **说的话会进聊天记录**：因为用的是当前话题，`MainActivity.appendToLastBubble` 会自动建气泡显示出来，
   我觉得这正好是"桌宠也在群里说话"的效果。不想污染聊天，就把 `topicId` 换成一个固定的桌宠话题 id
   （`TopicStore.findById` 取不到就先建一个），那边就不会显示。
3. **防插话**：`petRequestedTopic` 非空表示桌宠发起的那轮还在跑，此时再有 touch 直接忽略；
   你的 `ACTION_UPDATE` 是按 topicId 分线程攒字的，两句话不会串。

`poke` / `grab` / `pet` / `throw` / `drop` / `crash` 六种触感各有对应文案，
摸头不会被说成被拎起来。

## 7. 验证状态（照实说）

| 东西 | 状态 |
|---|---|
| `PetScript.java` | **真编译 + 真跑测试**：22 项断言全过（JDK 21，`javac --release 8`） |
| `PetAssetServer.java` | **真编译 + 真跑测试**：13 项断言全过，含 60 并发、目录穿越拒绝、`stop` 后端口关闭 |
| 4 个 Java 文件一起 | **对 android API 桩编译通过**，`--release 8` 无错无警——桩是我按真实签名写的（34 个类），能抓语法/类型/继承错误，**不等于在真 SDK 上编译过** |
| JS 侧（overlay.html + widget） | **39 项浏览器测试全绿**，含在**你 assets 同构目录**里实测：渲染、pointer 转发拎起落地、`setBox` 节流上报、touch 事件嵌套结构、跳起不出画层 |
| 真机（Android WebView + 悬浮窗） | **没跑过**，我没有设备也没有 Android SDK |

桩编译这两轮各抓到一个真错，都不是小错：
`FloatingPetWindow` 里 `density` 字段初始化引用了尚未赋值的 `ctx`；
`PetService` 广播接收器里局部变量叫 `topic`、我新加的代码用了 `topicId`。
都是靠编译才暴露的——你原来"没编译就先发"的那份也是这类问题。现在这版至少过了类型关。

## 8. 上真机的三步

```bash
# 1) 拷文件
cp -r arise-dropin/assets/coopanion/overlay.html arise-dropin/assets/coopanion/src arise-dropin/assets/coopanion/kit app/src/main/assets/coopanion/
cp arise-dropin/java/com/example/aries/*.java app/src/main/java/com/example/aries/
# 2) 原来那份 PetService.java 备份后替换；WhaleLiveView.java 可以留着不用（不影响编译）
# 3) 编译安装，抽屉里点"桌宠"（MainActivity.togglePet 已接好）
```

如果编译报错，大概率是这三处签名差异（桩与真 SDK 不同）：`getSystemService(Class)`（你原代码已在用，没问题）、
`WebView.evaluateJavascript(String, ValueCallback)`、`registerReceiver(BroadcastReceiver, IntentFilter, int)`（API 33 起才有，
我已用 `Build.VERSION.SDK_INT >= TIRAMISU` 分流，和你 MainActivity 里的写法一致）。

真机上要看的现象与排查：

- **白屏/画不出**：WebView 内核太老没有 WebGL2（`rig.js` 会自动退 WebGL1，两个都没有就画不出）。
  在 `PetService` 里临时把页面 load 到 `data:` 或加个日志看 `window.petCall('stats')` 有没有帧增长。
- **身体只有很小一只**：`scaleForStrip()` 里的 `dp(250f)` 是目标身高，改它即可；同时确认 `STRIP_DP` 够高（第 4 节公式）。
- **宠物被切**：加大 `STRIP_DP`，别改画布缩放——你原来 `liveView.setScale()` 那种改内容缩放的方式一定被 View 边界裁。
- **点不到它**：确认触摸层真的贴上了身体（看 `setBox` 有没有回调过来）；`onTouch return false` 不是穿透，是丢弃。
- **服务被系统杀**：`startForeground` 已发通知；仍被杀就去设置里关掉该应用的省电优化。
- **targetSdk 升到 34+** 时前台服务类型必须真的在跑，`specialUse` 已声明 ✓。

## 9. 想回原生（不用 WebView）的话

上一轮列的 14 条改动仍然有效（枢轴用 `model.pivots` 而不是 box 角点、命中框用部件并集而不是硬编码
`60..195/50..220`、`clampPosition` 要传像素、呼吸缩放要绕身体中心、`parts` 的线程竞争、RectF 复用……）。
最短路径是把 `rig.js` 的网格映射到 `Canvas.drawBitmapMesh`：`model.parts[].grid` 就是 mesh 行列数，
逐帧用同一套 `rot`/`warp` 变形器算顶点数组。数据我这边都是现成的，要的话我给你写这套顶点计算 + 测试。
