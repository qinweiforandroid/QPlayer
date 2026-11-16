# 调用链路

## 音频播放

```text
AudioPlayerFragment.onViewCreated
  │  首次进入：setQueue(createDemoQueue()) + updatePlaybackMode(ALL, 不随机)
  │
  ├─ 点击播放 ──► AudioPlaybackController.play(context, index)
  │                   └─ startForegroundService(ACTION_PLAY_INDEX, index)
  │
  └─ AudioPlaybackService.onStartCommand
         └─ runtime.play(index)
                └─ BasePlaybackRuntime.playWithRequirements
                       ├─ audioFocus.acquire()       失败则直接返回，不起播
                       ├─ wifiLock.acquire()
                       └─ session.play(index)
                              ├─ switchCurrentMedia → 通知 onPlaybackMediaChanged
                              ├─ sourceSelector.selectInitial(media)
                              ├─ 校验 source.isExpired
                              ├─ engine.setMedia(media, source, pendingStartPosition)
                              ├─ engine.prepare()
                              └─ engine.play()
```

状态回流：

```text
Media3PlayerEngine.Player.Listener
  → PlayerEventListener.onStateChanged
  → DefaultPlaybackSession.engineListener
      ├─ isEventFromCurrentMedia() 过滤旧媒体事件
      └─ 扇出到 PlaybackSessionListener
            ├─ AudioPlayerFragment  → 更新 UI
            ├─ AudioPlaybackService → 刷新通知
            └─ PlaybackNotification → 更新 RemoteViews
```

页面读状态用 `runtime.getSnapshot()`（同一条 session），命令用 `AudioPlaybackController` 走 Service。

## 暂停与焦点

```text
用户点暂停
  → AudioPlaybackController.pause(context)
  → Service: runtime.pause()
  → BasePlaybackRuntime.pause(resumeWhenFocusGain = false)
        ├─ audioFocus.onPlaybackPaused(false)   记录意图：下次 GAIN 不续播
        ├─ session.pause()
        └─ wifiLock.release()

系统广播 LOSS_TRANSIENT
  → AndroidAudioFocusController.onFocusChange
        ├─ resumeOnGain = defaultResumeOnGain   （音频 true / 视频 false）
        └─ onPausePlayback()
```

`AudioFocusRequestCompat` 只在首次 `acquire` 时构建并复用。复用同一个 request 不会触发「自我顶替」——否则新请求入栈会让系统向被顶掉的旧请求派发 `LOSS`，而旧请求绑的是同一个监听器，最终把自己刚起的播放暂停掉。

## 耳机拔出

```text
ACTION_AUDIO_BECOMING_NOISY
  → SystemNoisyAudioWatcher.onReceive
  → BasePlaybackRuntime.onBecameNoisy()
  → pause(resumeWhenFocusGain = false)
```

固定不续播：拔耳机后自动外放是不可接受的。

## 切歌

```text
session.skipToNext(auto)
  → playbackMode.skipPolicy().nextIndex(curIndex, size, auto)
     ├─ 线性：OFF 到边界返回 null；ALL 绕回
     ├─ 随机：随机取一个 != currentIndex 的项（size <= 1 时退回线性）
     └─ ONE：仅 auto = true 时返回当前索引
  → play(nextIndex) 走完整 resolveAndPlay
```

自动连播由 `onCompleted` 触发：先通知 `onPlaybackCompleted(media)`，再 `skipToNext(auto = true)`。

## 换源（清晰度 / 线路）

```text
session.switchSource(sourceId)
  ├─ 当前媒体不含该 sourceId → 返回 false，不做任何改动
  ├─ pendingStartPositionMs = 当前进度
  ├─ 更新队列中的媒体对象 defaultSourceId
  └─ play(updated) → resolveAndPlay
        └─ engine.setMedia(media, source, pendingStartPositionMs)
```

`pendingStartPositionMs` 消费一次即清零，所以换源后从原进度续播，而新起播仍从 0 开始。

VOD 页的清晰度切换、直播页的线路切换都复用这条路径。

## 失败降级

两级降级，先内核后会话：

```text
第一级：同一 source 原地重试（Media3PlayerEngine）
  onPlayerError
    ├─ Media3ErrorClassifier.classify(errorCode) → network / source / decode / unknown
    ├─ isRecoverable(kind) 且 retryAttempt < 3
    │     → scheduleRetry(delay = 1s << (attempt - 1))   即 1s / 2s / 4s
    └─ 否则：上报 telemetry.onError + listener.onError

第二级：换下一条 source（DefaultPlaybackSession）
  engineListener.onError(error)
    ├─ !error.recoverable 且 tryFallbackToNextSource() 成功 → 不上抛，静默续播
    └─ 否则 → 上抛 onPlaybackError(media, error)

  tryFallbackToNextSource
    ├─ failedSourceIds += 当前 sourceId
    ├─ sourceSelector.selectFallback(media, current, failedSourceIds)
    │     ├─ 优先同 qualityId 分组内未失败的线路
    │     └─ 分组内没有了才跨组兜底（避免线路重试换了清晰度）
    └─ 用当前进度重新 setMedia + prepare + play
```

只有网络类和资源类错误值得原地重试；解码失败重试不会自愈，直接交给上层。

## 直播重连

`LivePlaybackCoordinator` 在 core 之外编排三层自愈：

```text
start(channel, lineIndex)
  ├─ runtime.setMediaList([channel.toPlayableMedia()], 0)
  └─ playCurrentLine()

onPlaybackError
  ├─ 还有剩余线路 → currentLineIndex++，playCurrentLine()
  │     └─ switchingLine 保护窗口 1.5s，忽略上一条线路的迟到错误
  └─ 线路用尽 → scheduleReconnect()

scheduleReconnect
  ├─ attempt > 4 → allLinesFailed = true，停止重连
  ├─ attempt++，delay = 1s << (attempt - 1)
  └─ 延迟后 performReconnect()

performReconnect
  ├─ 轮换到下一线路（避免死磕同一条坏线路）
  └─ 起 8s watchdog：仍未 PLAYING 就继续下一次重连

onPlaybackStateChanged(PLAYING) 且 reconnecting → resetReconnect()
```

`resumeMonitoring()` 在页面回到前台时调用，会立即用当前快照重放一次状态，避免 UI 停在旧态。

## 文本朗读

```text
PlayableMedia.fromText(text)
  → PlaySource(inlineText = text)

TextPlayerEngine.setMedia
  ├─ resolveText：优先 source.inlineText，其次 media.extras["content"]
  ├─ TextSegmenter.split(text)
  │     └─ 句末标点（。！？；!?; 换行）断开；无标点的长文本按 120 字硬切
  └─ PREPARING

play()
  ├─ synthesizer.isAvailable() 为 false → ERROR(3001)
  ├─ 逐句 speak，作为独立 utterance 入队
  └─ ticker 按 progressUpdateIntervalMs 上报进度

UtteranceProgressListener.onDone
  → spokenCharCount = segment.endOffset
  → onSegmentFinished（供高亮跟随）
  → 最后一句 → ENDED + onCompleted → session 自动切下一项
```

进度换算：`ms = 字符数 / (charsPerSecond × speed) × 1000`。TTS 拿不到精确时长，对外时长全是估算；`seekTo` 只能定位到句首，句内不精确定位。

`UtteranceProgressListener` 的回调跑在 TTS 自己的后台线程，`AndroidTtsSynthesizer` 统一 post 回主线程再处理——否则 `utteranceSegments` 会被两个线程同时读写，且向下传导到 session / UI 的整条链路都要求主线程。

## 页面生命周期（视频）

```text
onCreate   →  runtime.initialize(this)
              runtime.bindPlayerView(playerView)
              runtime.addListener(listener)
              runtime.setMediaList(queue, index)

onStart    →  runtime.bindPlayerView(playerView)     重建被系统销毁的 Surface
              runtime.getSnapshot()                   同步手势上下文

onStop     →  runtime.pause()                         后台不自动恢复
              runtime.unbindPlayerView(playerView)    解绑悬空 Surface

onDestroy  →  runtime.removeListener(listener)
              runtime.unbindPlayerView(playerView)
              runtime.stop()
              runtime.release()                       释放 engine
```

`unbindPlayerView` 必须成对出现：页面不可见后 Surface 可能被系统销毁，不解绑会让 engine 持有悬空 surface。
