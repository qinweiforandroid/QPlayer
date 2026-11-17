# 模块与文件索引

逐文件说明。路径相对各模块的 `src/main/java/`。

## player-core

纯协议层，不依赖任何播放器实现。

### `com.qw.player.core.media`

| 文件 | 说明 |
| --- | --- |
| `PlayableMedia.kt` | 标准媒体对象。含 `resolveDefaultSource()` 与 `fromVideoUrl` / `fromAudioUrl` / `fromText` 三个快捷构造 |
| `PlaySource.kt` | 一条具体可播放来源。含 headers / UA / DRM / 字幕 / 裁剪 / 过期时间 / 缓存开关 / 清晰度分组 / 内联正文 |
| `PlayerMediaMetadata.kt` | 展示元信息：标题、副标题、作者、封面、extras |
| `MediaType.kt` | `AUDIO` / `VIDEO` / `TEXT`，决定 `RoutingPlayerEngine` 派给哪个 engine |
| `DrmConfiguration.kt` | 挂在 source 层的 DRM 配置（scheme / licenseUrl / 请求头 / multiSession） |
| `SubtitleTrack.kt` | 与 source 关联的一条可选字幕轨 |
| `ClipRange.kt` | source 级裁剪区间，用于试看、片段播放、章节内续播 |

### `com.qw.player.core.engine`

| 文件 | 说明 |
| --- | --- |
| `PlayerEngine.kt` | 内核协议。一次播放一条已解析 source，不管队列 |
| `RoutingPlayerEngine.kt` | 按 `MediaType` 派发；同实例去重、活跃类型翻转顺序等细节见 [architecture.md](architecture.md#路由) |
| `PlayerConfig.kt` | 启动策略：自动播放、焦点、后台、进度上报间隔、快进快退步长 |
| `PlayerEventListener.kt` | engine 层事件，每个回调只带事件本身的最小信息 |
| `PlaybackState.kt` | 九个统一状态 |
| `PlaybackSnapshot.kt` | 不可变运行时快照，附带 `isPlaying` / `isPaused` / `isBuffering` |
| `PlaybackError.kt` | 统一错误结构（code / message / recoverable / domain / category / extras） |
| `PlaybackErrorCategory.kt` | `NETWORK` / `SOURCE` / `DECODE` / `UNKNOWN`，供上层决定文案 |
| `PlayerCapabilities.kt` | 能力声明：视频输出、倍速、预加载、DRM、字幕、裁剪、文本高亮、速度区间 |
| `PlayerVideoOutput.kt` | 视频输出目标（`Surface` + `VideoScaleMode`） |
| `VideoSize.kt` | 宽高、旋转角度、像素宽高比 |
| `VideoScaleMode.kt` | `FIT` / `FILL` / `ZOOM` |

### `com.qw.player.core.session`

| 文件 | 说明 |
| --- | --- |
| `PlaybackSession.kt` | 会话协议：队列、索引、模式、切歌、换源、快照、监听 |
| `DefaultPlaybackSession.kt` | 默认实现。含 `isEventFromCurrentMedia` 过滤、`tryFallbackToNextSource` 降级、`pendingStartPositionMs` 续播 |
| `PlaybackSessionListener.kt` | 会话层事件，每条都携带所属 `PlayableMedia` |
| `PlaybackMode.kt` | `repeatMode` + `shuffleEnabled` |
| `RepeatMode.kt` | `OFF` / `ONE` / `ALL` |
| `SourceSelector.kt` | `selectInitial` / `selectFallback`；`DefaultSourceSelector` 优先同清晰度分组 |
| `SkipPolicy.kt` | internal。四种切歌算法：线性、随机、自动单曲重复装饰器，及 `PlaybackMode.skipPolicy()` 工厂 |

### `com.qw.player.core.telemetry`

| 文件 | 说明 |
| --- | --- |
| `PlaybackTelemetry.kt` | 四类事件：`PlaybackResultEvent`、`FirstFrameEvent`、`StallEvent`、`ErrorEvent` |

### `com.qw.player.core.common`

| 文件 | 说明 |
| --- | --- |
| `PlayLog.kt` | 统一日志出口 |
| `PodPlayerTimer.kt` | 进度轮询定时器，让各 engine 输出一致形状的进度事件 |

## player-runtime

运行时门面与 Android 平台策略。依赖 `player-core` 与 `androidx.media`。

| 文件 | 说明 |
| --- | --- |
| `BasePlaybackRuntime.kt` | 唯一门面。持 engine / session；编排「起播拿焦点和 WiFi 锁、暂停释放」；`initialize` 幂等，`reset` 彻底释放但保留监听者 |
| `PlaybackEngineFactory.kt` | `fun interface`，接入方提供已 `initialize` 的 `RoutingPlayerEngine` |
| `AudioFocusController.kt` | 焦点协议 + `AndroidAudioFocusController`（复用单个 `AudioFocusRequestCompat`，避免自我顶替） |
| `WifiLockKeeper.kt` | WiFi 高性能锁；`enabled = false` 时全部空操作 |
| `NoisyAudioWatcher.kt` | 耳机拔出 / 蓝牙断开广播监听 |
| `PlaybackListenerRegistry.kt` | internal。把 session 单槽位监听器扇出给多个观察者，保持注册顺序 |

## player-media3

| 文件 | 说明 |
| --- | --- |
| `Media3PlayerEngine.kt` | ExoPlayer 映射。状态/快照/进度/视频尺寸；网络重试；HTTP headers 与 UA；磁盘缓存；DRM、字幕、clipping；埋点 |
| `Media3ErrorClassifier.kt` | internal 纯逻辑对象：错误码归一、是否可重试、分类映射、退避时长。可脱离 ExoPlayer 单测 |

关键常量（`Media3PlayerEngine` companion）：

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `HTTP_CONNECT_TIMEOUT_MS` | 10_000 | HTTP 连接超时 |
| `HTTP_READ_TIMEOUT_MS` | 15_000 | HTTP 读取超时 |
| `CACHE_MAX_BYTES` | 256MB | 进程内共享 LRU 磁盘缓存上限 |
| `MIN_STALL_REPORT_MS` | 500 | 短于此值的 loading 不计入卡顿 |

`Media3ErrorClassifier`：`MAX_RETRY_ATTEMPTS = 3`，`RETRY_BASE_DELAY_MS = 1_000`。

## player-tts

| 文件 | 说明 |
| --- | --- |
| `TextPlayerEngine.kt` | 把系统 TTS 接入 `PlayerEngine`。进度按字符数估算；`seekTo` 只到句级；`setOnSegmentListener` 供逐句高亮 |
| `AndroidTtsSynthesizer.kt` | 基于 `android.speech.tts.TextToSpeech`；逐句作为独立 utterance；回调统一 post 回主线程 |
| `SpeechSynthesizer.kt` | 合成器协议 + `Listener`，方便替换实现 |
| `TextSegmenter.kt` | 正文切分：句末标点与换行断开，无标点长文本按 `maxSegmentChars` 硬切 |
| `ProgressTicker.kt` | 进度轮询协议 |
| `PodPlayerProgressTicker.kt` | 默认 `ProgressTicker` 实现 |

`TextSpeechConfig`：`charsPerSecond = 4.5f`，`maxSegmentChars = 120`，`progressIntervalMs = 500`。错误域 `"tts"`，错误码 3001（合成引擎不可用）/ 3002（合成失败）/ 3003（无正文）。

## app

demo 应用。包名 `com.qw.player.demo`。

### 根包

| 文件 | 说明 |
| --- | --- |
| `MyApplication.kt` | 持有应用级 `audioRuntime`；初始化并注入 `LogcatPlaybackTelemetry`；提供 `Context.audioRuntime` 扩展属性 |
| `MainActivity.kt` | demo 首页，三个入口卡片 |
| `FragmentHostActivity.kt` | 按类名装载 Fragment 的通用宿主 |
| `Constants.kt` | demo 用的示例媒体地址 |

### `demo.runtime`

| 文件 | 说明 |
| --- | --- |
| `VideoPlaybackRuntime.kt` | 视频 runtime（普通 class）。不依赖前台 Service，`resumeOnFocusGain = false`，每页自持实例 |
| `AudioPlaybackRuntime.kt` | 音频 runtime（普通 class）。允许后台播放、管理焦点；实例由 `MyApplication` 持有 |
| `AudioPlaybackService.kt` | 前台 Service，转发播放命令并驱动通知更新 |
| `AudioPlaybackController.kt` | 页面发命令的入口，统一经 `startForegroundService` 投递 Intent |
| `PlaybackNotification.kt` | 通知渲染（RemoteViews）+ 通知按钮广播接收 |
| `NotificationRenderer.kt` / `NotificationModel.kt` | 通知渲染协议与数据模型 |
| `DemoPlaybackEngineFactory.kt` | demo 装配：AUDIO/VIDEO 共用 Media3，TEXT 走 TTS；暴露 `media3Engine` 供 `bindPlayerView` 取 ExoPlayer |
| `LogcatPlaybackTelemetry.kt` | 埋点打到 Logcat |

### `demo.audio`

| 文件 | 说明 |
| --- | --- |
| `AudioPlayerFragment.kt` | 音频页。列表、播放模式、进度条、封面；首次进入才灌队列，Fragment 重建不打断播放 |

### `demo.vod`

| 文件 | 说明 |
| --- | --- |
| `VodPlayerActivity.kt` | 点播页。剧集列表、清晰度切换、倍速、全屏、手势 seek / 亮度 / 音量 |
| `VodRepository.kt` | demo 点播数据源 |
| `VodModels.kt` | `VodQuality` / `VodEpisode` / `VodPlaylist` |
| `VodMediaMapper.kt` | 点播数据 → `PlayableMedia`；清晰度映射为 `playSources` |

### `demo.cctv`

| 文件 | 说明 |
| --- | --- |
| `CctvChannelListActivity.kt` | 频道列表 |
| `CctvPlayerActivity.kt` | 直播播放页。EPG、频道侧栏、线路切换、音量 / 亮度手势 |
| `LivePlaybackCoordinator.kt` | 线路降级、断流重连（指数退避 + 线路轮换 + watchdog）；通过构造参数接收 runtime |
| `TvChannel.kt` | `ChannelCategory` / `TvLine` / `EpgEntry` / `TvChannel`；含 `currentEpg()` |
| `TvChannelRepository.kt` | demo 频道数据源 |
| `ChannelSidebarView.kt` | 频道切换侧栏 |

### `demo.widget`

| 文件 | 说明 |
| --- | --- |
| `MusicView.kt` | 音频页播放卡片 |
| `VodPlayerControlView.kt` | 点播控制栏 |
| `LivePlayerControlView.kt` | 直播控制栏 |
| `PlayerGestureLayout.kt` | 手势层：单击、双击、水平拖动 seek、垂直调亮度 / 音量、频道滑动 |
| `PlayerGestureHints.kt` | 手势反馈浮层 |

## app-tv

Android TV 接入示例。包名 `com.qw.player.tv`，界面全部用 Compose for TV（`androidx.tv:tv-material:1.1.0`）。

| 文件 | 说明 |
| --- | --- |
| `MainActivity.kt` | 单 Activity 入口，创建并在销毁时释放 `TvPlaybackRuntime` |
| `playback/TvPlaybackRuntime.kt` | TV 视频 runtime。不开后台播放；`getExoPlayer()` 供 `PlayerSurface` 使用 |
| `playback/TvPlaybackEngineFactory.kt` | 只注册 `MediaType.VIDEO`，没有 TTS 路由 |
| `ui/QPlayerTvApp.kt` | 页面路由（Home / Live / Vod），`BackHandler` 逐级返回；路由状态可跨进程重建 |
| `ui/PlayerStateHolder.kt` | 把 `PlaybackSessionListener` 事件转成 Compose 状态；`rememberPlayerState()` 自动注册 / 注销监听 |
| `ui/HomeScreen.kt` | 首页：按分类的频道卡片行 + 点播入口；首项自动请求焦点 |
| `ui/live/LivePlayerScreen.kt` | 直播页：`PlayerSurface` 全屏画面，底部渐变层内嵌播放 / 返回、线路、频道切换 |
| `ui/vod/VodPlayerScreen.kt` | 点播页：播放控制、前后 15 秒、清晰度、选集 |
| `ui/theme/Theme.kt` | `QPlayerTvTheme`：暗色 TV 配色，强调色沿用 demo 靛蓝 |
| `data/TvChannel.kt` / `TvChannelRepository.kt` | 频道模型与内置清单（40 个频道，多条线路） |
| `data/VodModels.kt` / `VodRepository.kt` | 点播模型与演示数据 |

接入差异只有两处：engine 只映射 `MediaType.VIDEO`；视频输出走 `media3-ui-compose` 的 `PlayerSurface` 而不是 `PlayerView`。
