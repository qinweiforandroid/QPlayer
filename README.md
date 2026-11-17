# QPlayer

QPlayer 是一个 Android 音视频播放内核工程，协议层、内核实现、运行时策略和 demo 应用分层清晰，四层可以单独替换。

```text
app          →  demo 应用：页面、前台 Service、通知、demo 装配
player-runtime →  运行时门面：音频焦点 / WiFi 锁 / 耳机拔出 / 监听扇出
player-media3  →  Media3（ExoPlayer）内核实现
player-tts     →  文本朗读（TTS）内核实现
player-core    →  播放协议：媒体模型、engine 协议、会话层、可观测性协议
```

依赖方向单向向下：`player-core` 不依赖任何其他模块，`app` 依赖全部。

## 快速开始

### 1. 引入模块

```groovy
// settings.gradle
include ':player-core'
include ':player-media3'
include ':player-tts'
include ':player-runtime'

// app/build.gradle
implementation project(path: ':player-core')
implementation project(path: ':player-media3')
implementation project(path: ':player-tts')
implementation project(path: ':player-runtime')
implementation "androidx.media3:media3-exoplayer:1.10.1"
implementation "androidx.media3:media3-ui:1.10.1"
```

本工程的库模块统一 `compileSdk 37` / `minSdk 23` / Java 17，通过根目录 `nexus.gradle` 发布 AAR。

### 2. 装配 engine

`BasePlaybackRuntime` 不关心「有哪些 engine」，只要求接入方提供一个已经组装好的 `RoutingPlayerEngine`，这一步由 `PlaybackEngineFactory` 表达。

```kotlin
class DemoPlaybackEngineFactory : PlaybackEngineFactory {

    lateinit var media3Engine: Media3PlayerEngine
        private set

    override fun create(context: Context, config: PlayerConfig): RoutingPlayerEngine {
        val created = Media3PlayerEngine(context)
        media3Engine = created
        return RoutingPlayerEngine(
            // 音频和视频共用同一个 Media3 实例，文本朗读交给 TTS
            engines = mapOf(
                MediaType.AUDIO to created,
                MediaType.VIDEO to created,
                MediaType.TEXT to TextPlayerEngine(AndroidTtsSynthesizer(context))
            ),
            defaultType = MediaType.VIDEO
        ).apply { initialize(config) }
    }
}
```

`RoutingPlayerEngine` 允许把多个 `MediaType` 映射到同一个 engine 实例（上面 AUDIO/VIDEO 就是），内部会按实例去重，避免后注册的类型覆盖先注册的监听器。

### 3. 创建 runtime 并初始化

```kotlin
class PlayerActivity : AppCompatActivity() {

    private val runtime = VideoPlaybackRuntime()   // 页面级实例

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime.initialize(this)                   // 幂等，重复调用被忽略
    }

    override fun onDestroy() {
        runtime.stop()
        runtime.release()                          // 释放 engine，必须调用
        super.onDestroy()
    }
}
```

`VideoPlaybackRuntime` / `AudioPlaybackRuntime` 是普通 class，不是单例。实例该活多久由调用方决定，见下文「runtime 实例放在哪里」。

### 4. 准备队列并播放

```kotlin
runtime.setMediaList(
    queue = listOf(
        PlayableMedia.fromVideoUrl("https://example.com/a.mp4", mediaId = "a"),
        PlayableMedia.fromText("这是一段文本朗读示例。", mediaId = "b", title = "朗读")
    ),
    startIndex = 0
)
runtime.play(0)
```

`PlayableMedia.fromVideoUrl` / `fromAudioUrl` / `fromText` 是单 source 的快捷构造；多码率、自定义 headers、DRM、字幕等场景用主构造函数写 `playSources`。

### 5. 监听播放事件

```kotlin
private val listener = object : PlaybackSessionListener {
    override fun onPlaybackStateChanged(media: PlayableMedia, state: PlaybackState) {
        // PLAYING / PAUSED / BUFFERING / ERROR ...
    }

    override fun onPlaybackProgress(media: PlayableMedia, positionMs: Long, durationMs: Long) {
        // 默认 1 秒一次，由 PlayerConfig.progressUpdateIntervalMs 控制
    }

    override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
        // error.category 决定文案方向，不必解析内核错误码
    }
}

runtime.addListener(listener)
// 页面销毁时记得 removeListener
```

### 6. 把视频绑到 PlayerView

```kotlin
runtime.bindPlayerView(bind.playerView)     // onStart 或 onCreate 里重建 surface
runtime.unbindPlayerView(bind.playerView)   // onStop 里解绑，避免 engine 持有悬空 Surface
```

`bindPlayerView` 直接取 `DemoPlaybackEngineFactory.media3Engine` 的 ExoPlayer 挂到 `PlayerView` 上，所以视频页面必须用定义了 `media3Engine` 字段的 factory。

### 7. 运行 demo

```bash
./gradlew :app:assembleDebug
./gradlew installDebug
```

demo 首页三个入口分别进入音频页、直播页、点播页。

### 8. 运行 TV 应用

```bash
./gradlew :app-tv:assembleDebug
./gradlew :app-tv:installDebug
```

装到电视或 TV 模拟器后，从 Android TV 桌面（`LEANBACK_LAUNCHER`）启动。

## 运行环境

| 项 | 版本 |
| --- | --- |
| Gradle | 9.4.1 |
| AGP | 9.2.0 |
| Kotlin | 2.3.10 |
| compileSdk / targetSdk | 37 |
| minSdk | 23 |
| Java | 17 |
| Media3 | 1.10.1 |

## 仓库结构

```text
QPlayer/
├── app/                    # demo 应用、前台 Service、通知、页面接线
├── app-tv/                 # Android TV 应用（Compose for TV）
├── player-core/            # 播放协议（无 Android 播放器依赖）
├── player-runtime/         # 运行时门面与平台策略
├── player-media3/          # Media3 内核实现
├── player-tts/             # 文本朗读内核实现
├── docs/ai/                # 面向 AI 与团队的结构说明
├── nexus.gradle            # 库模块 AAR 发布配置
└── settings.gradle
```

## 模块职责

### `player-core` — 协议层

不含任何播放器实现，只定义协议：

| 包 | 内容 |
| --- | --- |
| `core.media` | `PlayableMedia`、`PlaySource`、`PlayerMediaMetadata`、`MediaType`、`DrmConfiguration`、`SubtitleTrack`、`ClipRange` |
| `core.engine` | `PlayerEngine`、`RoutingPlayerEngine`、`PlayerConfig`、`PlayerEventListener`、`PlaybackSnapshot`、`PlaybackState`、`PlaybackError`、`PlayerCapabilities`、`PlayerVideoOutput`、`VideoSize`、`VideoScaleMode` |
| `core.session` | `PlaybackSession`、`DefaultPlaybackSession`、`PlaybackSessionListener`、`PlaybackMode`、`RepeatMode`、`SourceSelector`、`SkipPolicy`（internal） |
| `core.telemetry` | `PlaybackTelemetry`（首帧 / 卡顿 / 错误 / 播放结果） |
| `core.common` | `PlayLog`、`PodPlayerTimer` |

### `player-runtime` — 运行时

`BasePlaybackRuntime` 是唯一的门面类，只做三件事：持有 initialize 装配出的 engine / session；把指令转交；编排「播放前拿焦点和 WiFi 锁、暂停后释放」。具体机制各自收口在协议后面：

| 组件 | 职责 |
| --- | --- |
| `PlaybackEngineFactory` | engine 装配，由接入方实现 |
| `AudioFocusController` | 焦点申请、失焦响应、是否续播 |
| `WifiLockKeeper` | 后台播放期间的 WiFi 高性能锁 |
| `NoisyAudioWatcher` | 耳机拔出 / 蓝牙断开广播 |
| `PlaybackListenerRegistry` | session 单槽位监听器 → 多观察者扇出 |

### `player-media3` — Media3 内核

`Media3PlayerEngine` 把 ExoPlayer 映射成统一协议：状态、快照、进度、视频尺寸、错误分类。`Media3ErrorClassifier` 是纯逻辑对象，负责错误归类与重试判定，可脱离 ExoPlayer 单测。

### `player-tts` — 文本朗读内核

`TextPlayerEngine` 把系统 `TextToSpeech` 接入 `PlayerEngine` 协议。TTS 拿不到精确时长，对外进度和总时长都按 `TextSpeechConfig.charsPerSecond` 从「已读字符数」折算；`seekTo` 只能定位到句级。逐句高亮通过独立的 `setOnSegmentListener` 暴露，不污染 `PlayerEventListener`。

### `app` — demo

音频页、点播页、直播频道列表 / 播放页，外加音频前台 Service 与通知。

### `app-tv` — TV

同一套播放内核的 Android TV 接入示例，界面全部用 **Compose for TV**（`androidx.tv:tv-material`）实现，遵循官方 TV 设计指南：48dp 横向安全区、卡片聚焦放大 1.1 倍 + 发丝边框、暗底高对比配色、遥控器 D-Pad 全程可操作。

| 目录 | 内容 |
| --- | --- |
| `tv.MainActivity` | 单 Activity 入口，持有一个 `TvPlaybackRuntime` |
| `tv.playback` | `TvPlaybackRuntime`（只路由视频）、`TvPlaybackEngineFactory` |
| `tv.ui` | `QPlayerTvApp` 路由、`PlayerStateHolder`（runtime 事件 → Compose 状态）、首页 |
| `tv.ui.live` | 直播页：频道 / 线路切换控件铺在画面底部渐变层上，不弹全屏列表 |
| `tv.ui.vod` | 点播页：播放控制、清晰度、选集 |
| `tv.ui.theme` | 暗色 TV 配色，强调色沿用 demo 的靛蓝 |

接入层的差异只有两处：engine 只注册 `MediaType.VIDEO`；视频画面直接挂给 `media3-ui-compose` 的 `PlayerSurface`，不经过 `PlayerView`。

## 核心概念

### `PlayableMedia` 与 `PlaySource`

一个媒体对象可以挂多条 source，用来表达多码率、多 CDN、多线路、在线 / 离线版本。`defaultSourceId` 指定首选，`resolveDefaultSource()` 在缺省时兜底到第一条。

`PlaySource.qualityId` 标记清晰度分组：同组内的多条 source 视为同一清晰度的不同线路。降级时 `DefaultSourceSelector` 会先在当前分组内找未失败的线路，找不到才跨组兜底，避免线路重试时意外换了清晰度。

几个容易踩的字段：

- `cacheable`：直播流必须传 `false`，否则会进磁盘缓存
- `expiresAtMs`：`resolveAndPlay` 会用 `isExpired` 校验，过期直接报错不起播
- `inlineText`：仅 `MediaType.TEXT` 用，正文内联时 `url` 可以为空

### 三层状态模型

```text
PlaybackState        统一状态枚举（IDLE / PREPARING / READY / PLAYING / PAUSED / BUFFERING / ENDED / ERROR / RELEASED）
PlaybackSnapshot     某一时刻的完整运行时快照，UI / 通知 / 埋点都应优先消费它
PlaybackError        统一错误结构（code / message / recoverable / domain / category）
```

上层不需要自己拼多个 getter，也不需要解析内核原始错误码。

### `PlayerEngine` 与 `PlaybackSession` 的分工

| | `PlayerEngine` | `PlaybackSession` |
| --- | --- | --- |
| 管什么 | 一次播放一条已解析的 source | 队列、当前索引、播放模式、切歌 |
| 不管什么 | 队列、下一首决策、source 解析 | 解码与渲染 |
| 事件协议 | `PlayerEventListener`（事件本身的最小信息） | `PlaybackSessionListener`（每条事件携带所属 `PlayableMedia`） |

session 只认识一个 engine，靠 `RoutingPlayerEngine` 按 `MediaType` 派活。同一时刻只有一个 `MediaType` 活跃，非活跃 engine 的事件会被丢弃，避免上一次播放的残留回调（例如 ENDED）干扰当前会话。

### 播放模式

`PlaybackMode(repeatMode, shuffleEnabled)` 交给 `skipPolicy()` 生成策略对象，`DefaultPlaybackSession` 不再写分支：

- `RepeatMode.OFF` / `ALL`：线性前进，`ALL` 越界绕回
- `RepeatMode.ONE`：只约束**自动**切歌，用户手动切歌照常前进（用装饰器叠加，不影响手动行为）
- `shuffleEnabled`：随机跳到任意其他项，队列不足两项退回线性

## runtime 实例放在哪里

这是接入时最需要想清楚的一件事，两个 runtime 的答案不一样。

**视频 runtime：页面级。** 每个播放页自己 `VideoPlaybackRuntime()`，`onCreate` 初始化、`onDestroy` 释放。CCTV 和 VOD 之间不共享 engine，页面退出即回收解码资源。

**音频 runtime：应用级。** 音频页、前台 Service、通知读写的是同一条 session，如果每处各建一个实例就会把 session 拆成多份。所以实例挂在 `MyApplication` 上：

```kotlin
class MyApplication : Application() {
    val audioRuntime = AudioPlaybackRuntime()

    override fun onCreate() {
        super.onCreate()
        audioRuntime.initialize(this)
        audioRuntime.setTelemetry(LogcatPlaybackTelemetry())
    }
}
```

页面与 Service 通过扩展属性取用：

```kotlin
val Context.audioRuntime: AudioPlaybackRuntime
    get() = (applicationContext as MyApplication).audioRuntime
```

## 主链路

### 音频

`AudioPlayerFragment` 只发命令（`AudioPlaybackController` 经 `startForegroundService` 投递），真正驱动 runtime 的是 `AudioPlaybackService`：

```text
AudioPlayerFragment
  → AudioPlaybackController          （Intent 命令）
  → AudioPlaybackService             （前台 Service，持有通知）
  → AudioPlaybackRuntime             （应用级实例）
  → DefaultPlaybackSession
  → RoutingPlayerEngine → Media3PlayerEngine / TextPlayerEngine
  ← PlaybackSessionListener          （页面、Service、通知各自订阅）
```

页面订阅的是 `MyApplication.audioRuntime` 的同一条 session，所以直接读 `getSnapshot()` 就是实时状态。

### 视频

```text
VodPlayerActivity / CctvPlayerActivity
  → VideoPlaybackRuntime             （页面级实例）
  → DefaultPlaybackSession
  → RoutingPlayerEngine → Media3PlayerEngine

CctvPlayerActivity
  → LivePlaybackCoordinator(runtime) （线路降级、断流重连）
```

直播的线路切换复用 `switchSource` 的思路，但降级链和指数退避重连由 `LivePlaybackCoordinator` 自己编排，不进入 core。

## 关键入口

| 位置 | 说明 |
| --- | --- |
| [app/src/main/java/com/qw/player/demo/MyApplication.kt](app/src/main/java/com/qw/player/demo/MyApplication.kt) | 应用启动，初始化音频 runtime 与埋点 |
| [app/src/main/java/com/qw/player/demo/MainActivity.kt](app/src/main/java/com/qw/player/demo/MainActivity.kt) | demo 首页 |
| [app/src/main/java/com/qw/player/demo/audio/AudioPlayerFragment.kt](app/src/main/java/com/qw/player/demo/audio/AudioPlayerFragment.kt) | 音频页 |
| [app/src/main/java/com/qw/player/demo/vod/VodPlayerActivity.kt](app/src/main/java/com/qw/player/demo/vod/VodPlayerActivity.kt) | 点播页，清晰度切换 |
| [app/src/main/java/com/qw/player/demo/cctv/CctvPlayerActivity.kt](app/src/main/java/com/qw/player/demo/cctv/CctvPlayerActivity.kt) | 直播页 |
| [app/src/main/java/com/qw/player/demo/cctv/LivePlaybackCoordinator.kt](app/src/main/java/com/qw/player/demo/cctv/LivePlaybackCoordinator.kt) | 直播线路降级与断流重连 |
| [app/src/main/java/com/qw/player/demo/runtime/](app/src/main/java/com/qw/player/demo/runtime/) | 音频 / 视频 runtime、前台 Service、通知 |
| [player-runtime/src/main/java/com/qw/player/runtime/BasePlaybackRuntime.kt](player-runtime/src/main/java/com/qw/player/runtime/BasePlaybackRuntime.kt) | 运行时门面 |
| [player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt](player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt) | 会话层实现 |
| [player-core/src/main/java/com/qw/player/core/engine/RoutingPlayerEngine.kt](player-core/src/main/java/com/qw/player/core/engine/RoutingPlayerEngine.kt) | 按 MediaType 路由 |
| [player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt](player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt) | Media3 内核 |
| [player-tts/src/main/java/com/qw/player/tts/TextPlayerEngine.kt](player-tts/src/main/java/com/qw/player/tts/TextPlayerEngine.kt) | 文本朗读内核 |

## 深入阅读

1. [docs/ai/architecture.md](docs/ai/architecture.md) — 分层与协议设计
2. [docs/ai/module-index.md](docs/ai/module-index.md) — 逐文件索引
3. [docs/ai/call-flow.md](docs/ai/call-flow.md) — 播放、切歌、换源、降级的调用链
4. [docs/ai/playback-resilience.md](docs/ai/playback-resilience.md) — 容错与生命周期设计决策

## 构建

```bash
./gradlew :app:assembleDebug          # demo APK
./gradlew :player-core:assemble       # 协议层 AAR
./gradlew :player-runtime:assemble
./gradlew :player-media3:assemble
./gradlew :player-tts:assemble
```

release 构建已开启 `minifyEnabled` + `shrinkResources`。
