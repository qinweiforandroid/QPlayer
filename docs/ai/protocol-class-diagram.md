# Protocol Layer Class Diagram

`player-core` 协议层（`engine` / `media` / `source` / `session` / `telemetry`）当前结构的类图快照，用于评审阶段参考。代码才是最终事实来源，本文档若与代码不一致，以代码为准。

## session 包：会话协调层

```mermaid
classDiagram
    class PlaybackSession {
        <<interface>>
        +bindEngine(engine)
        +setSourceResolver(resolver)
        +setQueue(queue, startIndex)
        +getQueue() List~PlayableMedia~
        +getCurrentMedia() PlayableMedia
        +getCurrentIndex() Int
        +updatePlaybackMode(mode)
        +getPlaybackMode() PlaybackMode
        +play(index|mediaId|media)
        +pause()
        +resume()
        +stop()
        +seekTo(positionMs)
        +skipToNext(auto)
        +skipToPrevious(auto)
        +getSnapshot() PlaybackSnapshot
        +addListener(listener)
        +removeListener(listener)
        +release()
    }

    class DefaultPlaybackSession {
        -queue: MutableList~PlayableMedia~
        -listeners: Set~PlaybackSessionListener~
        -engine: PlayerEngine
        -resolver: MediaSourceResolver
        -playbackMode: PlaybackMode
        -currentIndex: Int
        -currentMedia: PlayableMedia
        -latestSnapshot: PlaybackSnapshot
        -engineListener: PlayerEventListener
        -resolveAndPlay(media)
        -tryFallbackToNextSource() Bool
        -findNextIndex(auto) Int?
        -findPreviousIndex(auto) Int?
    }

    class PlaybackSessionListener {
        <<interface>>
        +onPlaybackMediaChanged(current, previous)
        +onPlaybackMediaCleared()
        +onPlaybackStateChanged(media, state)
        +onPlaybackProgress(media, positionMs, durationMs)
        +onPlaybackBufferingProgress(media, bufferedPositionMs)
        +onPlaybackCompleted(media)
        +onPlaybackError(media, error)
    }

    class PlaybackMode {
        <<data>>
        +repeatMode: RepeatMode
        +shuffleEnabled: Boolean
    }

    class RepeatMode {
        <<enum>>
        OFF
        ONE
        ALL
    }

    PlaybackSession <|.. DefaultPlaybackSession
    DefaultPlaybackSession ..> PlayerEngine : bindEngine / 驱动
    DefaultPlaybackSession ..> PlayerEventListener : 实现(内部匿名对象)
    DefaultPlaybackSession ..> MediaSourceResolver : setSourceResolver
    DefaultPlaybackSession "1" o-- "*" PlaybackSessionListener : listeners
    DefaultPlaybackSession "1" o-- "*" PlayableMedia : queue
    DefaultPlaybackSession --> PlaybackMode
    PlaybackMode --> RepeatMode
    PlaybackSessionListener ..> PlaybackError
    PlaybackSessionListener ..> PlaybackState
    PlaybackSessionListener ..> PlayableMedia
```

## engine 包：播放内核协议

```mermaid
classDiagram
    class PlayerEngine {
        <<interface>>
        +initialize(config)
        +setMedia(media, source, startPositionMs)
        +preload(media, source)
        +prepare()
        +play()
        +pause()
        +stop(reset)
        +seekTo(positionMs)
        +setPlaybackSpeed(speed)
        +setLooping(looping)
        +setVolume(volume)
        +setMuted(muted)
        +attachVideoOutput(output)
        +setListener(listener)
        +setTelemetry(telemetry)
        +getSnapshot() PlaybackSnapshot
        +getCapabilities() PlayerCapabilities
        +release()
    }

    class PlayerEventListener {
        <<interface>>
        +onStateChanged(state)
        +onProgress(positionMs, durationMs)
        +onRenderedFirstFrame()
        +onVideoSizeChanged(size)
        +onBufferingProgress(bufferedPositionMs, durationMs)
        +onCompleted()
        +onError(error)
    }

    class PlayerConfig {
        <<data>>
        +autoPlayWhenReady: Boolean
        +respectAudioFocus: Boolean
        +allowBackgroundPlayback: Boolean
        +enablePreload: Boolean
        +rememberPlaybackPosition: Boolean
        +progressUpdateIntervalMs: Long
        +preferredSeekBackMs: Long
        +preferredSeekForwardMs: Long
    }

    class PlayerCapabilities {
        <<data>>
        +supportsVideoOutput: Boolean
        +supportsSpeedControl: Boolean
        +supportsPreload: Boolean
        +supportsBackgroundPlayback: Boolean
        +supportsHeaders: Boolean
        +supportsDrm: Boolean
        +supportsSubtitles: Boolean
        +supportsClipping: Boolean
        +minSpeed: Float
        +maxSpeed: Float
    }

    class PlaybackSnapshot {
        <<data>>
        +state: PlaybackState
        +currentMediaId: String
        +currentSourceId: String
        +isPrepared: Boolean
        +playWhenReady: Boolean
        +currentPositionMs: Long
        +durationMs: Long
        +bufferedPositionMs: Long
        +bufferedPercent: Int
        +speed: Float
        +volume: Float
        +isMuted: Boolean
        +videoSize: VideoSize
        +error: PlaybackError
    }

    class PlaybackState {
        <<enum>>
        IDLE
        INITIALIZED
        PREPARING
        READY
        PLAYING
        PAUSED
        BUFFERING
        ENDED
        ERROR
        RELEASED
    }

    class PlaybackError {
        <<data>>
        +code: Int
        +message: String
        +recoverable: Boolean
        +domain: String
        +extras: Map
    }

    class PlayerVideoOutput {
        <<data>>
        +surface: Surface
        +scaleMode: VideoScaleMode
    }

    class VideoScaleMode {
        <<enum>>
        FIT
        FILL
        ZOOM
    }

    class VideoSize {
        <<data>>
        +width: Int
        +height: Int
        +rotationDegrees: Int
        +pixelWidthHeightRatio: Float
    }

    PlayerEngine ..> PlayerConfig
    PlayerEngine ..> PlayerCapabilities
    PlayerEngine ..> PlaybackSnapshot
    PlayerEngine ..> PlayerVideoOutput
    PlayerEngine "1" --> "0..1" PlayerEventListener : setListener
    PlayerEngine ..> PlaybackTelemetry : setTelemetry
    PlayerEngine ..> PlayableMedia
    PlayerEngine ..> PlaySource
    PlayerEventListener ..> PlaybackState
    PlayerEventListener ..> PlaybackError
    PlayerEventListener ..> VideoSize
    PlaybackSnapshot --> PlaybackState
    PlaybackSnapshot --> VideoSize
    PlaybackSnapshot --> PlaybackError
    PlayerVideoOutput --> VideoScaleMode
```

## media 包：媒体模型

```mermaid
classDiagram
    class PlayableMedia {
        <<data>>
        +mediaId: String
        +mediaType: MediaType
        +metadata: PlayerMediaMetadata
        +playSources: List~PlaySource~
        +defaultSourceId: String
        +extras: Map
        +resolveDefaultSource() PlaySource
    }

    class MediaType {
        <<enum>>
        AUDIO
        VIDEO
    }

    class PlayerMediaMetadata {
        <<data>>
        +title: String
        +subtitle: String
        +artist: String
        +album: String
        +description: String
        +coverUrl: String
        +durationMs: Long
        +extras: Map
    }

    class PlaySource {
        <<data>>
        +sourceId: String
        +url: String
        +mimeType: String
        +headers: Map
        +userAgent: String
        +drm: DrmConfiguration
        +subtitles: List~SubtitleTrack~
        +clipRange: ClipRange
        +expiresAtMs: Long
        +cacheable: Boolean
        +isExpired(nowMs) Boolean
    }

    class DrmConfiguration {
        <<data>>
        +scheme: String
        +licenseUrl: String
        +requestHeaders: Map
        +multiSession: Boolean
    }

    class SubtitleTrack {
        <<data>>
        +id: String
        +label: String
        +url: String
        +mimeType: String
        +language: String
        +isDefault: Boolean
    }

    class ClipRange {
        <<data>>
        +startPositionMs: Long
        +endPositionMs: Long
    }

    class ResolvedMedia {
        <<data>>
        +mediaId: String
        +playSources: List~PlaySource~
        +defaultSourceId: String
        +metadata: PlayerMediaMetadata
        +resolveDefaultSource() PlaySource
    }

    PlayableMedia --> MediaType
    PlayableMedia --> PlayerMediaMetadata
    PlayableMedia "1" o-- "*" PlaySource
    PlaySource --> DrmConfiguration
    PlaySource "1" o-- "*" SubtitleTrack
    PlaySource --> ClipRange
    ResolvedMedia "1" o-- "*" PlaySource
    ResolvedMedia --> PlayerMediaMetadata
```

## source 包：解析边界

```mermaid
classDiagram
    class MediaSourceResolver {
        <<interface>>
        +resolve(media, callback)
    }
    class ResolverCallback {
        <<interface>>
        +onSuccess(result)
        +onFailure(error)
    }
    MediaSourceResolver ..> PlayableMedia
    MediaSourceResolver *-- ResolverCallback : 内部接口
    ResolverCallback ..> ResolvedMedia
    ResolverCallback ..> PlaybackError
```

## telemetry 包：可观测性协议

```mermaid
classDiagram
    class PlaybackTelemetry {
        <<interface>>
        +onPlaybackResult(event)
        +onFirstFrame(event)
        +onStall(event)
        +onError(event)
    }
    PlaybackTelemetry ..> PlaybackError
```

## 结构速览

- **engine 包**(底层内核协议):`PlayerEngine` 为核心接口,围绕它的是配置 (`PlayerConfig`)、能力声明 (`PlayerCapabilities`)、运行时快照 (`PlaybackSnapshot` + `PlaybackState`)、事件回调 (`PlayerEventListener`)、视频输出 (`PlayerVideoOutput` + `VideoScaleMode` + `VideoSize`) 和统一错误 (`PlaybackError`)。`PlayerEventListener` 遵循最少知道原则,每个回调只携带该事件必需的最小参数(`onStateChanged(state)`、`onProgress(positionMs, durationMs)`、`onBufferingProgress(bufferedPositionMs, durationMs)`、`onCompleted()`、`onError(error)`);需要完整状态的消费者应自行调用 `PlayerEngine.getSnapshot()` 拉取,`PlaybackSnapshot` 只作为拉取式 API 的返回值,不再作为事件推送负载。
- **media 包**(媒体模型):`PlayableMedia` 持有多条 `PlaySource`,`PlaySource` 又挂载 DRM / 字幕 / 裁剪区间。`ResolvedMedia` 是 resolver 输出的轻量更新结构,与 `PlayableMedia` 共享 `resolveDefaultSource()` 策略(存在重复代码)。
- **source 包**:仅一个边界接口 `MediaSourceResolver`,内嵌 `Callback`,是 session 与业务解析逻辑之间的唯一耦合点。
- **session 包**(会话协调层):`PlaybackSession` 接口 + 唯一实现 `DefaultPlaybackSession`,组合 `PlayerEngine`、`MediaSourceResolver`、队列 (`List<PlayableMedia>`)、`PlaybackMode`(`RepeatMode`)。`PlaybackSessionListener` 本轮同样按最少知道原则收窄:删除了无真实消费者的 `onQueueChanged`/`onPlaybackModeChanged`;`onCurrentMediaChanged(current: PlayableMedia?, previous)` 拆成 `onPlaybackMediaChanged(current: PlayableMedia, previous)`(current 不再可空)+ `onPlaybackMediaCleared()`(专门表达"没有任何选中项了");其余回调(`onPlaybackStateChanged`/`onPlaybackProgress`/`onPlaybackBufferingProgress`/`onPlaybackCompleted`/`onPlaybackError`)都显式携带事件所属的 `PlayableMedia`——这是跟 engine 层不同的地方:engine 一次只认一个 source,"是哪个"没有歧义,但 session 生命周期内会经历多条 media,省略 media 会让只订阅部分回调的消费者丢上下文。`DefaultPlaybackSession` 内部用匿名类实现 `PlayerEventListener` 桥接 engine 事件,大多数回调直接转发 engine 推来的精简参数并附上自己持有的 `currentMedia`,无需反查 `engine.getSnapshot()`;只有 `onError` 内部仍会 `pullSnapshot()`,因为 `tryFallbackToNextSource()` 的降级逻辑需要 `currentSourceId`/`currentPositionMs`,但这只是 session 内部实现细节,不影响对外转发的精简信号。
- **telemetry 包**:`PlaybackTelemetry` 独立可插拔协议,仅依赖 `PlaybackError`,不感知 session/media。
