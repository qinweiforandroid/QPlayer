# Playback Resilience

本文记录播放链路上三个**非显然**的设计决策。改动相关代码前请先读完，
否则很容易把已经规避掉的坑重新踩一遍。

## 1. 两层重试分工：engine 内重试，session 外降级

**顺序是刻意的，不要合并成一层。**

```text
错误发生
  └─ Media3PlayerEngine
       ├─ 可恢复错误 → 同一条 source 指数退避重试（1s / 2s / 4s，最多 3 次）
       │               重试仍失败 → 把 recoverable=false 的错误抛给 session
       └─ 不可恢复错误 → 直接抛给 session
  └─ DefaultPlaybackSession
       └─ 收到不可恢复错误 → 换同媒体的下一条 source（线路降级）
            全部 source 都失败 → 才上报给 UI
```

**为什么这样分工：**

- engine 只知道「当前这一条 source」，它没有队列、也没有别的线路信息，
  所以它能做的只有「同一条 source 再试一次」。这是网络抖动、5xx、
  清单暂时取不到这类瞬时故障的正确解法。
- session 持有 `PlayableMedia.playSources` 全量列表，换线路是它的职责。

**如果把两件事都放进 engine**，engine 就要反向依赖队列模型，破坏
「engine 只负责播，不负责队列」的协议约束。

**如果颠倒顺序**（session 先换线路、再让 engine 重试），
一次弱网抖动就会导致直接切到备用线路，而备用线路往往质量更差，
用户体验反而下降。

**实现位置：**

- `Media3PlayerEngine.scheduleRetry` / `cancelPendingRetry`
- `Media3ErrorClassifier.isRecoverable`
- `DefaultPlaybackSession.resolveAndPlay` 的降级分支

**`cancelPendingRetry` 的调用点很重要：** `setMedia`（切换媒体）、
`stop(reset=true)`、`release` 都要清理挂起的重试任务，
否则会出现「旧 source 的重试打到新媒体上」的问题。
`LivePlaybackCoordinator` 的 `reconnectWatchdog` 是同一类坑，已经一并处理。

## 2. `PlaySource.cacheable` 的语义

- **默认 `true`**：点播场景应该缓存。
- **直播 / HLS 必须显式传 `false`**：直播分片是无限增长的时间轴，
  写进磁盘缓存只会白白占空间，且几乎没有命中收益。

**为什么放在 `PlaySource` 而不是 `PlayerConfig`：** 同一个会话里可能同时
存在可缓存的点播和不可缓存的直播，配置粒度必须落到 source 级别。

**当前实现细节：** 磁盘缓存是进程内单例（`getSharedCache`），
上限硬编码 256MB，LRU 淘汰。多个 engine 实例共享同一个缓存目录，
避免音频 / 视频 runtime 各占一份。

## 3. 过期 source 的拦截位置

**在 `DefaultPlaybackSession`，不在 engine。**

`PlaySource.expiresAtMs` 命中过期时，session 会先调用
`MediaSourceResolver` 重新解析，拿到新 URL 后再交给 engine。

**为什么不放 engine：**

- 重新 resolve 是 `MediaSourceResolver` 的职责。
- engine 不应该感知 resolver 的存在——这会让 `player-media3`
  反向依赖 `player-core/source`，破坏分层。
- 过期判断是纯数据判断（比较时间戳），session 层做成本最低。

**为什么不能不做：** 如果不拦截，过期 URL 会交给 engine，
engine 只能通过 HTTP 401/403 失败来「发现」过期，
白白多一次网络往返，而且会让错误分类统计里混入大量假故障。

**实现位置：** `DefaultPlaybackSession.resolveAndPlay`

## 4. 资源与生命周期的三条约定

这三条都在 `app` 层，不影响 core / media3 协议：

1. **拔耳机即暂停**：`BasePlaybackRuntime` 监听
   `AudioManager.ACTION_AUDIO_BECOMING_NOISY`，收到后
   `pause(resumeWhenFocusGain = false)`。不能复用音频焦点回调，
   因为拔耳机不一定触发焦点变化。
2. **WifiLock 只给后台音频**：`allowBackgroundPlayback = true` 的 runtime
   在播放期间持有 `WIFI_MODE_FULL_HIGH_PERF`，避免熄屏后 WiFi 进入省电模式
   导致缓冲中断。视频页不持有。**没有引入 WakeLock**：前台 Service + 通知
   已经能防止系统回收，先验证是否真的有必要再加。
3. **视频 Surface 随页面可见性走**：视频页 `onStop` 解绑
   `PlayerView.player`，`onStart` 重新绑定。页面不可见后 Surface 可能被系统
   销毁，engine 若继续持有就会出现悬空引用。

## 5. 可观测性

`PlaybackTelemetry` 是 core 层协议，只产出事件、不感知上报后端：

- `onFirstFrame`：从 `setMedia` 到首帧可渲染的耗时
- `onStall`：进入 loading 到恢复播放，**短于 500ms 的抖动不计入**
- `onError`：终态错误（重试耗尽后的那次，不是每次重试）
- `onPlaybackResult`：`COMPLETED` / `STOPPED` / `FAILED` + 实际播放时长

**注意：** 同一条 source 的重试（`scheduleRetry` → `setMedia`）
不会重置会话统计，否则播放时长和首帧计时会被反复清零。
只有 `mediaId` 或 `sourceId` 真正变化时才开启新会话。
