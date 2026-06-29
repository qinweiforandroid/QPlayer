# Media3 Engine

## 目标

`player-media3` 提供当前唯一的播放内核实现：

- `Media3PlayerEngine`

它实现的是 `player-core/engine/PlayerEngine` 协议。

## 核心职责

- 接收 `PlayableMedia + PlaySource`
- 驱动 Media3 / ExoPlayer
- 输出统一的 `PlaybackSnapshot`
- 输出统一的 `PlayerEventListener` 事件
- 支持视频输出绑定

## 当前能力

- 音频播放
- 视频播放
- 进度回调
- 缓冲状态回调
- 倍速
- 第一帧回调
- 视频尺寸回调

## 当前边界

`Media3PlayerEngine` 当前还没有完全展开这些能力：

- DRM 深度接入
- 字幕轨管理
- source clipping 的完整实现
- 更细粒度的 renderer / track 选择

这些都可以继续在 `player-core` 协议不变的前提下往里补。

## 关键文件

- `player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt`
