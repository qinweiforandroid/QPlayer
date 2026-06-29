# Module Index

## `app`

职责：

- 音频播放页
- 列表播放页
- 视频播放页
- 音频前台 Service
- 音频 / 视频双 runtime

关键文件：

- `app/src/main/java/com/qw/player/demo/MyApplication.kt`
- `app/src/main/java/com/qw/player/demo/MainActivity.kt`
- `app/src/main/java/com/qw/player/demo/audio/AudioPlayerFragment.kt`
- `app/src/main/java/com/qw/player/demo/audio/PlayListFragment.kt`
- `app/src/main/java/com/qw/player/demo/video/VideoMedia3PlayerViewActivity.kt`
- `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackRuntime.kt`
- `app/src/main/java/com/qw/player/demo/runtime/VideoPlaybackRuntime.kt`
- `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackService.kt`
- `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackController.kt`
- `app/src/main/java/com/qw/player/demo/runtime/PlaybackNotification.kt`

## `player-core`

职责：

- 核心协议
- 媒体模型
- source resolver
- session 抽象与默认实现

包结构：

- `common`
- `media`
- `source`
- `engine`
- `session`

关键文件：

- `player-core/src/main/java/com/qw/player/core/engine/PlayerEngine.kt`
- `player-core/src/main/java/com/qw/player/core/media/PlayableMedia.kt`
- `player-core/src/main/java/com/qw/player/core/source/MediaSourceResolver.kt`
- `player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt`

## `player-media3`

职责：

- Media3 内核实现

关键文件：

- `player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt`

## 最重要的三个入口

如果只读 3 个文件，优先读：

1. `player-core/src/main/java/com/qw/player/core/engine/PlayerEngine.kt`
2. `player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt`
3. `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackRuntime.kt`
