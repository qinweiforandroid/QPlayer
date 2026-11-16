# 容错与生命周期设计决策

这份文档记录「为什么这么做」而不是「代码写了什么」——后者直接看源码更准确。只收录不看代码容易误判、或者看代码也容易忽略的决策。

## 重试与降级为什么分两级

**第一级（`Media3PlayerEngine` 内部）：同一 source 原地重试。**

网络抖动、CDN 瞬时故障这类问题，原地重试往往就能恢复，没必要换 source 或打扰上层。`Media3ErrorClassifier` 只允许 `network` 和 `source` 两类错误重试，`decode` 失败不重试——解码失败通常是编码格式问题，原地重试不会自愈，直接重试只会浪费时间并延迟真正的降级。退避是 1s / 2s / 4s（`1000 shl (attempt - 1)`），封顶 3 次。

**第二级（`DefaultPlaybackSession`）：换一条 source。**

第一级重试耗尽后，`engine.onError` 上报 `recoverable = false`。session 这时不是立刻把错误甩给 UI，而是先问 `SourceSelector` 还有没有没试过的线路/清晰度。`DefaultSourceSelector` 的策略是先在同 `qualityId` 分组内找，分组内没有了才跨组——这是为了避免「线路 A 卡了，自动重试却把清晰度也降了」这种对用户不可预期的副作用。只有两级都用尽，才真正 `onPlaybackError` 上抛。

直播页的 `LivePlaybackCoordinator` 是第三级，但它不在 core 里，是业务层基于 `PlaybackSessionListener.onPlaybackError` 自己编排的（线路轮换 + 断流重连），因为「一个频道有几条线路、重连策略多激进」是业务决策，不该下沉到协议层。

## `RoutingPlayerEngine` 切换活跃类型的顺序

```kotlin
activeType = targetType               // 先翻转
if (engines[previousType] !== engines[targetType]) {
    engines[previousType]?.stop(reset = true)   // 再 stop
}
```

顺序反过来会出 bug：`stop()` 内部同步触发 `onStateChanged`，如果这时 `activeType` 还没翻转，`Forwarder.isActive()` 判断为真，旧 engine 的 stop 事件就会被转发成「当前活跃媒体的状态变化」，污染新一轮播放的状态流。

另外，新旧类型如果指向同一个 engine 实例（音频和视频共用 Media3），不能 stop 它——它接下来马上要被新类型继续使用，stop 会打断本该连续的播放。

## 为什么 session 要校验 `isEventFromCurrentMedia`

`curMedia` / `curIndex` 可以在不碰 engine 的情况下被改变（比如 `setMediaList` 换了队列但没有立刻 `play`），多 engine 路由场景下也存在「旧 engine 还没真正切换走」的窗口期。如果这时旧 engine 吐出一个迟到的事件（比如上一条媒体的 ENDED），直接转发会让 UI 看到「当前媒体播放完成」的错误信号。

校验方式是比较 `engine.getSnapshot().currentMediaId` 和 `curMedia.mediaId`；如果 engine 还没上报过任何 `currentMediaId`（比如从没 `setMedia` 过），放行，避免破坏「快照字段不全」这个 engine 实现里的既有行为。

## 音频焦点：为什么 `AudioFocusRequestCompat` 要复用

```kotlin
val currentRequest = request ?: buildRequest().also { request = it }
```

如果每次 `acquire()` 都 new 一个新 request，新请求入栈时系统会把旧请求判定为被顶替，向它派发 `AUDIOFOCUS_LOSS`——而旧请求绑定的监听器和新请求是同一个对象，最终效果是「刚申请到焦点的播放立刻被自己的失焦回调暂停掉」。复用同一个 request 对象，重复 `acquire()` 是幂等的，不会触发这个问题。

## 耳机拔出为什么固定不续播

`NoisyAudioWatcher` 触发时调用 `pause(resumeWhenFocusGain = false)`，不管构造时 `resumeOnFocusGain` 是什么。音频焦点的「失焦后是否续播」是一个策略选项（短暂占用后可能想自动恢复），但耳机拔出是用户的物理动作，语义上等同于「不想让别人听到」，不应该被任何焦点恢复逻辑覆盖。

## 视频页为什么要 `bindPlayerView` / `unbindPlayerView` 成对出现

Activity 不可见时，`Surface` 可能被系统提前销毁（尤其是切后台、锁屏）。如果不在 `onStop` 解绑，`ExoPlayer` 会继续持有一个已经失效的 `Surface` 引用，`onStart` 重新可见时如果不重新 `bindPlayerView`，画面会黑屏。这也是为什么 `onStart` 要判断 `bind.mPlayerView.player == null` 才重新绑——避免每次 `onStart` 都做一次不必要的重新挂载。

## 视频 runtime 为什么是页面级实例而音频是应用级

**视频：页面级。** 同一时间只会有一个视频页面在前台播放（CCTV 和 VOD 不会同时存在），且视频没有后台播放需求，页面销毁时就该把 engine 整个释放掉，归还解码器资源。如果用单例，退出页面后 engine 不会被释放，`ExoPlayer` 实例会一直占着解码器直到进程结束。

**音频：应用级。** 音频需要在页面不可见时继续播（前台 Service + 通知），而且通知的播放/暂停按钮点击后要能让音频页面看到最新状态——页面、Service、通知三者必须共享同一条 `DefaultPlaybackSession`，否则会出现「通知显示在播，页面显示暂停」这种状态分裂。所以音频 runtime 挂在 `Application` 上，比任何一个页面活得都久。

## WifiLock 为什么只给音频开、不给视频开

`WifiLockKeeper` 由 `allowBackgroundPlayback` 控制是否生效（`enabled` 参数）。视频没有后台播放场景，锁屏/切后台就暂停了，没必要在前台播放时额外持有 WiFi 高性能锁——那只会增加功耗，收益为零。音频需要在熄屏后台持续播放，这时 WiFi 可能被系统降到省电模式导致缓冲中断，所以音频 runtime 构造时传 `allowBackgroundPlayback = true`，连带打开 WifiLock。

同样原因，视频 runtime 不刻意加 WakeLock——没有需求就不加，避免过度设计。

## 缓存：为什么直播必须 `cacheable = false`

`Media3PlayerEngine` 用 `PlaySource.cacheable` 决定是否套 `CacheDataSource`。直播流是持续增长的时间轴，如果被当成普通文件缓存，`SimpleCache` 会给它分配磁盘空间却永远用不完，白白消耗存储；而且直播场景根本不需要「重新播放同一段」这个缓存收益。所以直播的 `PlaySource` 构造时必须显式传 `cacheable = false`。

缓存本身是进程内共享的单例（`sharedCache`，256MB，LRU 淘汰），目的是避免多个 engine 实例各开一个缓存目录互相抢占磁盘。

## TTS 的进度为什么是估算值，`seekTo` 为什么只能到句级

系统 `TextToSpeech` 不会提前告诉你「这段文本念出来要多久」，唯一可靠的信号是「这一句念完了」（`UtteranceProgressListener.onDone`）。所以 `TextPlayerEngine` 的时间模型整体是按「字符数 / 语速」估算出来的近似值，不是真实回放位置。

`seekTo` 只能定位到句子边界，是因为 TTS 没有「从某个字开始念」这种能力——一旦开始合成一个 utterance，只能从头播完或者整句重放，没有句内随机访问点。

## `PlaybackSnapshot` 为什么要做成不可变快照而不是让 UI 查多个 getter

如果 UI 分别调用 `isPlaying()` / `getCurrentPosition()` / `getDuration()`，这几次调用之间状态可能已经变化（尤其是跨线程读取 ExoPlayer 内部状态时），UI 拼出来的信息可能互相矛盾。`getSnapshot()` 保证返回的是单次调用内的自洽视图，`isPlaying` / `isPaused` / `isBuffering` 这些派生属性也是基于同一个 `state` 字段算出来的，不会出现「state 是 PAUSED 但 isPlaying 是 true」这种不一致。
