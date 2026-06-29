# Core Protocol

虽然历史上这套协议是从 “V2” 演进过来的，但当前仓库里它已经是唯一主协议。

## 协议分层

### `media`

- `PlayableMedia`
- `PlaySource`
- `PlayerMediaMetadata`
- `ResolvedMedia`
- `MediaType`

### `source`

- `MediaSourceResolver`

### `engine`

- `PlayerEngine`
- `PlayerConfig`
- `PlayerEventListener`
- `PlaybackSnapshot`
- `PlaybackState`
- `PlaybackError`
- `PlayerCapabilities`
- `PlayerVideoOutput`

### `session`

- `PlaybackSession`
- `PlaybackSessionListener`
- `PlaybackMode`
- `RepeatMode`
- `DefaultPlaybackSession`

## 设计原则

- 媒体模型独立于播放内核
- source 解析独立于媒体模型
- engine 只负责播，不负责队列
- session 负责队列和切歌
- app 通过 runtime 把 session / engine / service 串起来

## 运行时映射

- 音频：`AudioPlaybackRuntime`
- 视频：`VideoPlaybackRuntime`

## 当前事实

这套协议已经不是过渡协议，而是仓库当前唯一的正式协议。
