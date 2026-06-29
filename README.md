# QPlayer

QPlayer 是一个基于 Android 的音视频播放器工程，当前主架构已经完成收敛，核心由三部分组成：

- `player-core`
  - 播放协议
  - 媒体模型
  - source 解析接口
  - 播放会话层
- `player-media3`
  - 基于 Media3 的播放内核实现
- `app`
  - 音频播放页
  - 音频列表播放页
  - 视频播放页
  - 音频前台 Service
  - 音频 / 视频双 runtime

当前仓库已经移除了旧播放链路，代码主线只保留一套稳定结构。

## 推荐阅读顺序

1. [AGENTS.md](AGENTS.md)
2. [docs/ai/architecture.md](docs/ai/architecture.md)
3. [docs/ai/module-index.md](docs/ai/module-index.md)
4. [docs/ai/call-flow.md](docs/ai/call-flow.md)
5. [docs/ai/repository-index.json](docs/ai/repository-index.json)

## 仓库结构

```text
QPlayer/
├── app/                    # Demo 应用、前台 Service、runtime
├── player-core/            # 播放协议与会话层
├── player-media3/          # Media3 播放内核实现
├── docs/ai/                # 面向 AI 的结构说明
├── build.gradle
├── settings.gradle
└── AGENTS.md
```

## 模块职责

### `app`

- 初始化双 runtime
- 提供音频前台 Service
- 提供音频、列表、视频示例页面
- 负责页面与 runtime 的接线

### `player-core`

- 定义播放器核心协议
- 定义媒体模型
- 定义 source resolver
- 定义 session 协议与默认实现

### `player-media3`

- 提供 `Media3PlayerEngine`
- 负责把 Media3 状态映射成统一协议

## `player-core` 包结构

`player-core` 已按功能拆包：

- `com.qw.player.core.common`
  - `PlayLog`
  - `PodPlayerTimer`
- `com.qw.player.core.media`
  - `PlayableMedia`
  - `PlaySource`
  - `PlayerMediaMetadata`
  - `MediaType`
  - `ResolvedMedia`
  - `ClipRange`
  - `DrmConfiguration`
  - `SubtitleTrack`
- `com.qw.player.core.source`
  - `MediaSourceResolver`
- `com.qw.player.core.engine`
  - `PlayerEngine`
  - `PlayerConfig`
  - `PlayerEventListener`
  - `PlaybackSnapshot`
  - `PlaybackState`
  - `PlaybackError`
  - `PlayerCapabilities`
  - `PlayerVideoOutput`
  - `VideoSize`
  - `VideoScaleMode`
- `com.qw.player.core.session`
  - `PlaybackSession`
  - `PlaybackSessionListener`
  - `PlaybackMode`
  - `RepeatMode`
  - `DefaultPlaybackSession`

## 运行时结构

当前运行时拆成两套：

### 音频 runtime

- `AudioPlaybackRuntime`
- `AudioPlaybackController`
- `AudioPlaybackService`
- `PlaybackNotification`

特点：

- 管理音频焦点
- 支持后台播放
- 有前台通知和 service

### 视频 runtime

- `VideoPlaybackRuntime`

特点：

- 不依赖音频前台 Service
- 不管理音频焦点
- 生命周期由视频页面自己驱动

## 当前主链路

### 音频主链路

1. `MyApplication` 初始化 `AudioPlaybackRuntime`
2. 页面通过 `AudioPlaybackController` 发命令
3. `AudioPlaybackService` 驱动 `AudioPlaybackRuntime`
4. `AudioPlaybackRuntime` 持有 `DefaultPlaybackSession + Media3PlayerEngine`
5. `DefaultPlaybackSession` 调用 `MediaSourceResolver`
6. `Media3PlayerEngine` 负责真实播放
7. `PlaybackNotification` 同步通知状态

### 视频主链路

1. `MyApplication` 初始化 `VideoPlaybackRuntime`
2. 视频页直接操作 `VideoPlaybackRuntime`
3. `VideoPlaybackRuntime` 持有独立的 `DefaultPlaybackSession + Media3PlayerEngine`
4. 视频播放不与音频队列互相覆盖

## 关键入口

- App 启动入口: `app/src/main/java/com/qw/player/demo/MyApplication.kt`
- Demo 首页: `app/src/main/java/com/qw/player/demo/MainActivity.kt`
- 音频播放页: `app/src/main/java/com/qw/player/demo/audio/AudioPlayerFragment.kt`
- 列表播放页: `app/src/main/java/com/qw/player/demo/audio/PlayListFragment.kt`
- 视频播放页: `app/src/main/java/com/qw/player/demo/video/VideoMedia3PlayerViewActivity.kt`
- 音频 runtime: `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackRuntime.kt`
- 视频 runtime: `app/src/main/java/com/qw/player/demo/runtime/VideoPlaybackRuntime.kt`
- 音频 Service: `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackService.kt`
- Media3 引擎: `player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt`

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :player-core:assemble
./gradlew :player-media3:assemble
```

## 当前状态

- 旧播放链路已移除
- 命名和包结构已完成整理
- `player-core` 已按功能分包
- 音频 / 视频 runtime 已拆分
- `:app:assembleDebug` 已验证通过
