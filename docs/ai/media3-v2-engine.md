# Media3 Engine

## 目标

`player-media3` 提供当前唯一的播放内核实现：

- `Media3PlayerEngine`（实现 `player-core/engine/PlayerEngine`）
- `Media3ErrorClassifier`（错误分类与重试策略，纯逻辑，无 Android 依赖）

## 核心职责

- 接收 `PlayableMedia + PlaySource`
- 驱动 Media3 / ExoPlayer
- 输出统一的 `PlaybackSnapshot`
- 输出统一的 `PlayerEventListener` 事件
- 支持视频输出绑定
- 在可恢复错误上做原地重试
- 产出可观测性事件

## 当前能力

| 能力 | 实现位置 |
| --- | --- |
| 音频 / 视频播放 | `ExoPlayer` |
| 进度、缓冲、首帧、视频尺寸回调 | `Player.Listener` + `PodPlayerTimer` |
| 倍速 / 循环 / 音量 / 静音 | `setPlaybackSpeed` / `setLooping` / `setVolume` / `setMuted` |
| 自定义请求头、UA、连接与读取超时 | `DefaultHttpDataSource.Factory` |
| 跨协议重定向 | `setAllowCrossProtocolRedirects(true)` |
| 磁盘缓存（LRU 256MB） | `SimpleCache` + `CacheDataSource.Factory`，按 `PlaySource.cacheable` 开关 |
| 片段裁剪 | `PlaySource.clipRange` → `MediaItem.ClippingConfiguration` |
| DRM | `PlaySource.drm` → `MediaItem.DrmConfiguration`（Widevine / PlayReady / ClearKey） |
| 字幕轨 | `PlaySource.subtitles` → `MediaItem.SubtitleConfiguration` |
| 可恢复错误重试 | `onPlayerError` + `scheduleRetry` |
| 可观测性事件 | `PlaybackTelemetry` |

## 当前边界

以下能力仍未实现，可以在 `player-core` 协议不变的前提下继续补：

- 音轨 / 字幕轨 / 视频轨的手动选择（当前只按 media 自带轨道信息自动选择）
- 更细粒度的 renderer 选择
- 缓存大小与位置的运行时配置（当前硬编码 `CACHE_MAX_BYTES = 256MB`，单例贯穿进程）
- DRM 离线许可证的预下载与续期

`getCapabilities()` 的声明已经与实现对齐，不要再把它当作「计划列表」。

## 关键文件

- `player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt`
- `player-media3/src/main/java/com/qw/player/media3/Media3ErrorClassifier.kt`
- `player-media3/src/test/java/com/qw/player/media3/Media3ErrorClassifierTest.kt`

## 相关文档

- 重试 / 降级 / 缓存三者的分工见 [playback-resilience.md](playback-resilience.md)
