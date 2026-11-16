# 架构

## 分层

```text
┌──────────────────────────────────────────────┐
│ app                                          │  页面 / Service / 通知 / demo 装配
├──────────────────────────────────────────────┤
│ player-runtime                               │  BasePlaybackRuntime + 平台策略
├───────────────────────┬──────────────────────┤
│ player-media3         │ player-tts           │  PlayerEngine 实现
├───────────────────────┴──────────────────────┤
│ player-core                                  │  协议层，不依赖任何播放器
└──────────────────────────────────────────────┘
```

依赖严格单向：`player-core` 不依赖其他任何模块；`player-media3` / `player-tts` / `player-runtime` 只依赖 `player-core`；`app` 依赖全部。

## 为什么这么分

**协议层与实现层分开。** `PlayerEngine` 是一个只有 20 个方法的接口，Media3、TTS 各实现一份。上层代码只面向接口，换内核不动业务；测试也可以塞假的 engine。

**会话层不属于内核。** 队列、当前索引、播放模式、切歌策略是「播放列表语义」，与解码渲染无关。放进 session 后，任何 engine 都能复用同一套队列逻辑。

**平台策略单独成模块。** 音频焦点、WiFi 锁、耳机拔出广播是 Android 平台特有的，不属于「播放协议」。它们原来堆在 demo 的 runtime 里，现在下沉到 `player-runtime`，因此 `player-core` 保持纯净，`player-runtime` 可以被任何 Android 接入方直接复用。

## 三层职责

### 协议层 `player-core`

把「播放器能做什么」抽象成稳定的数据契约：

- `PlayableMedia` / `PlaySource`：媒体与它的可播放来源
- `PlayerEngine`：一次播放一条已解析 source 的能力集合
- `PlaybackSession`：队列与会话语义
- `PlayerEventListener` / `PlaybackSessionListener`：两级事件协议
- `PlaybackState` / `PlaybackSnapshot` / `PlaybackError`：统一状态、快照、错误
- `PlaybackTelemetry`：可观测性产出协议，后端由业务实现

### 实现层 `player-media3` / `player-tts`

各自实现 `PlayerEngine`，把各自内核的状态、错误、进度映射成统一协议。共同约束：

- 一次只挂一条 source
- 事件通过单槽位 `setListener` 上报
- `getSnapshot()` 必须能在任意时刻返回自洽快照

### 运行时层 `player-runtime`

`BasePlaybackRuntime` 是门面，只做编排，不做实现：

```text
initialize()  →  factory.create() 拿 engine
              →  DefaultPlaybackSession().bindEngine()
              →  按需装配 AudioFocusController / WifiLockKeeper / NoisyAudioWatcher

play/resume   →  拿焦点 → 持 WiFi 锁 → 下发 session
pause         →  通知焦点控制器暂停意图 → session.pause() → 放 WiFi 锁
stop          →  session.stop() → 放 WiFi 锁 → 放弃焦点
```

平台能力的每个机制都藏在协议后面（`PlaybackEngineFactory`、`AudioFocusController`、`WifiLockKeeper`、`NoisyAudioWatcher`），runtime 只表达意图。这些协议都有 `fun interface` 形式的 Factory，测试或换平台时可以替换。

## 路由

`RoutingPlayerEngine` 解决「session 只认识一个 engine，但实际有多个内核」的矛盾。

```text
engines: Map<MediaType, PlayerEngine>
   AUDIO → Media3PlayerEngine ┐
   VIDEO → Media3PlayerEngine ├─ 同一实例，按实例去重
   TEXT  → TextPlayerEngine   ┘
```

两个关键设计：

1. **按实例去重 setListener。** `PlayerEngine.setListener` 只有单槽位。如果按 Map entry 逐个调用，AUDIO 和 VIDEO 指向同一实例时后注册的会覆盖先注册的，该引擎服务另一类型时会被误判为「不活跃」而吞掉所有事件。所以按实例去重，`Forwarder` 判断的是「当前活跃类型是否在这个引擎负责的类型集合里」。

2. **切换活跃类型时先翻转再 stop。** `stop()` 内部会同步触发 `onStateChanged`。如果先 stop 再翻转 `activeType`，这条回调会在翻转前被判定为「仍活跃」而错误转发。另外，新旧类型共用同一实例时不应 stop——它马上要被新类型继续使用。

## 事件两级协议

| | `PlayerEventListener` | `PlaybackSessionListener` |
| --- | --- | --- |
| 定位 | engine 层，事件本身的最小信息 | 会话层，带队列语义 |
| 参数 | `onStateChanged(state)`、`onProgress(pos, dur)` | `onPlaybackStateChanged(media, state)` |
| 消费者 | session 内部 | 页面、通知、Service |
| 多观察者 | 单槽位 | 通过 `PlaybackListenerRegistry` 扇出 |

session 层每条事件都显式携带 `PlayableMedia`，消费者不必自己维护「这条事件属于哪条 media」的状态。

## 状态模型

`PlaybackState` 九个状态：`IDLE`、`INITIALIZED`、`PREPARING`、`READY`、`PLAYING`、`PAUSED`、`BUFFERING`、`ENDED`、`ERROR`、`RELEASED`。

`PlaybackSnapshot` 是某一时刻的完整视图，附带 `isPlaying` / `isPaused` / `isBuffering` 三个派生属性。UI、通知、埋点都应消费快照，而不是自己拼多个 getter 或缓存多个状态字段。

## 可观测性

`PlaybackTelemetry` 定义四类事件：播放结果、首帧耗时、卡顿、错误。core 只产出事件，不感知上报平台；业务实现接口后注入 `setTelemetry`，传 `null` 关闭。demo 的实现是 `LogcatPlaybackTelemetry`，把事件打到 Logcat。

卡顿判定阈值 500ms；同一 source 的重试不会重置统计上下文。
