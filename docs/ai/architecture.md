# Architecture

## 1. 一句话理解

QPlayer 当前是“一套核心协议 + 一套 Media3 内核 + 两套 runtime（音频 / 视频）+ 一个 demo app”。

## 2. 分层结构

```text
UI Layer
└── app
    ├── AudioPlayerFragment
    ├── PlayListFragment
    ├── VideoMedia3PlayerViewActivity
    ├── AudioPlaybackService
    ├── PlaybackNotification
    ├── AudioPlaybackRuntime
    └── VideoPlaybackRuntime

Session Layer
└── player-core/session
    ├── PlaybackSession
    ├── PlaybackSessionListener
    ├── PlaybackMode / RepeatMode
    └── DefaultPlaybackSession

Engine Layer
├── player-core/engine
│   ├── PlayerEngine
│   ├── PlayerEventListener
│   ├── PlaybackSnapshot
│   └── PlayerConfig / PlayerCapabilities
└── player-media3
    └── Media3PlayerEngine

Source Layer
└── player-core/source
    └── MediaSourceResolver

Media Layer
└── player-core/media
    ├── PlayableMedia
    ├── PlaySource
    ├── PlayerMediaMetadata
    ├── ResolvedMedia
    ├── DrmConfiguration
    ├── SubtitleTrack
    └── ClipRange

Common Layer
└── player-core/common
    ├── PlayLog
    └── PodPlayerTimer
```

## 3. 双 runtime 结构

### 音频 runtime

- `AudioPlaybackRuntime`
- `AudioPlaybackService`
- `AudioPlaybackController`
- `PlaybackNotification`

职责：

- 后台播放
- 音频焦点
- 前台通知
- 音频队列会话

### 视频 runtime

- `VideoPlaybackRuntime`

职责：

- 独立视频会话
- 不与音频队列互相覆盖
- 页面直接驱动

## 4. 核心依赖方向

```text
app -> player-core
app -> player-media3

player-media3 -> player-core
```

## 5. 当前主链路

### 音频

1. `MyApplication` 初始化 `AudioPlaybackRuntime`
2. 页面通过 `AudioPlaybackController` 发命令
3. `AudioPlaybackService` 收到 action
4. `AudioPlaybackRuntime` 调用 `DefaultPlaybackSession`
5. `DefaultPlaybackSession` 通过 `MediaSourceResolver` 获取可播 source
6. `Media3PlayerEngine` 执行真实播放
7. `PlaybackNotification` 同步通知状态

### 视频

1. `MyApplication` 初始化 `VideoPlaybackRuntime`
2. 视频页设置单独视频队列
3. `VideoPlaybackRuntime` 驱动 `DefaultPlaybackSession`
4. `Media3PlayerEngine` 执行真实播放

## 6. 架构特点

- 旧播放链路已移除
- 核心协议已按功能分包
- 音频 / 视频运行时解耦
- app 层只保留一套新的命令链路
