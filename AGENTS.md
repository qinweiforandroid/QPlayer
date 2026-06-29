# AGENTS.md

本文件面向进入 `QPlayer` 仓库协作的 AI / 自动化代理，目标是帮助快速建立当前代码结构的稳定上下文。

## 1. 项目定位

QPlayer 当前是一套收敛后的 Android 音视频播放器工程：

- `player-core`
  - 播放协议
  - 媒体模型
  - source resolver
  - session 层
- `player-media3`
  - Media3 播放内核实现
- `app`
  - 音频 / 列表 / 视频页面
  - 音频前台 Service
  - 音频 / 视频双 runtime

当前代码主线已经不再保留旧播放链路。

## 2. 推荐理解顺序

每次进入仓库，优先按下面顺序阅读：

1. `settings.gradle`
2. `docs/ai/repository-index.json`
3. `docs/ai/architecture.md`
4. `docs/ai/module-index.md`
5. `player-core/src/main/java/com/qw/player/core/engine/PlayerEngine.kt`
6. `player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt`
7. `app/src/main/java/com/qw/player/demo/runtime/AudioPlaybackRuntime.kt`
8. `app/src/main/java/com/qw/player/demo/runtime/VideoPlaybackRuntime.kt`
9. `player-media3/src/main/java/com/qw/player/media3/Media3PlayerEngine.kt`

## 3. 模块边界

### `player-core`

- 只放核心协议与基础模型
- 按职责拆包，不要重新回到平铺结构
- 任何协议变更都要评估 `app` 和 `player-media3`

### `player-media3`

- 当前唯一播放内核实现
- 如果出现 prepare、进度、状态、视频输出异常，优先看这里

### `app`

- 负责 runtime、service、notification、页面
- 音频和视频是两套 runtime
- 页面不应直接持有独立 player 之外的额外架构层

## 4. 当前运行事实

- 音频由 `AudioPlaybackRuntime` 管理
- 视频由 `VideoPlaybackRuntime` 管理
- 音频有前台 Service 与通知
- 视频不依赖音频前台 Service
- `DefaultPlaybackSession` 是当前唯一 session 实现
- `MediaSourceResolver` 是 source 解析入口

## 5. 常见定位策略

### 查“音频为什么没开始”

按顺序看：

1. `MyApplication` 是否初始化了 `AudioPlaybackRuntime`
2. 页面是否通过 `AudioPlaybackController` 发命令
3. `AudioPlaybackService` 是否收到 action
4. `DefaultPlaybackSession` 是否解析出 source
5. `Media3PlayerEngine` 是否进入 `READY/PLAYING`

### 查“视频为什么覆盖了音频”

先确认当前实现里是否误把页面接到了 `AudioPlaybackRuntime`。

正确链路应该是：

- 音频页 -> `AudioPlaybackRuntime`
- 视频页 -> `VideoPlaybackRuntime`

### 查“切歌 / 播放模式异常”

按顺序看：

1. `player-core/src/main/java/com/qw/player/core/session/DefaultPlaybackSession.kt`
2. `player-core/src/main/java/com/qw/player/core/session/PlaybackMode.kt`
3. 页面是否正确调用了 runtime 的 `updatePlaybackMode(...)`

### 查“通知或后台播放行为”

按顺序看：

1. `AudioPlaybackService`
2. `PlaybackNotification`
3. `AudioPlaybackController`
4. `AudioPlaybackRuntime`

## 6. 修改建议

- 优先在现有双 runtime 结构上迭代，不要再把音频和视频重新揉回一个 runtime
- `player-core` 内部改动优先保持按功能分包
- 如果改 `PlayerEngine` / `PlaybackSession`，要同步评估 `player-media3` 和 `app/runtime`
- 如果改通知链路，要同步评估 `AudioPlaybackController`、`AudioPlaybackService`、`PlaybackNotification`

## 7. 验证命令

```bash
./gradlew :app:assembleDebug
./gradlew :player-core:assemble
./gradlew :player-media3:assemble
```

## 8. 已知注意点

- 当前文档已按收敛后的代码结构重写
- 代码是最终事实来源
- `:app:assembleDebug` 已在当前结构下验证通过
