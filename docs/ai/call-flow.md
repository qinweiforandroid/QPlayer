# Call Flow

## 1. 应用初始化

1. `MyApplication.onCreate()`
2. 初始化应用级播放 runtime 和媒体源解析器
3. 创建统一的 `MediaSourceResolver`
4. 初始化 `AudioPlaybackRuntime`
5. 初始化 `VideoPlaybackRuntime`

## 2. 音频播放链路

1. 页面调用 `AudioPlaybackController.play(context, index)`
2. `AudioPlaybackController` 启动 `AudioPlaybackService`
3. `AudioPlaybackService` 解析 action
4. `AudioPlaybackRuntime.play(index)`
5. `DefaultPlaybackSession.play(index)`
6. `MediaSourceResolver.resolve(media, callback)`
7. 解析成功后得到 `ResolvedMedia`
8. `DefaultPlaybackSession` 选出默认 `PlaySource`
9. `Media3PlayerEngine.setMedia(...)`
10. `Media3PlayerEngine.prepare()`
11. `Media3PlayerEngine.play()`
12. `PlaybackNotification` 随状态更新

## 3. 列表切歌链路

1. 页面调用 `AudioPlaybackController.skipToNext(...)` 或 `skipToPrevious(...)`
2. `AudioPlaybackService` 转发给 `AudioPlaybackRuntime`
3. `DefaultPlaybackSession` 根据 `PlaybackMode` 决定下一个 index
4. 再次进入播放链路

## 4. 视频播放链路

1. 进入 `VideoMedia3PlayerViewActivity`
2. 页面把自己的 `PlayableMedia` 组装成单元素队列
3. `VideoPlaybackRuntime.setQueue(...)`
4. `VideoPlaybackRuntime.play(0)`
5. `DefaultPlaybackSession` 解析并驱动 `Media3PlayerEngine`
6. `PlayerView` 绑定 `VideoPlaybackRuntime.getEngine().getExoPlayer()`

## 5. 通知控制链路

1. 用户点击通知按钮
2. `PlaybackNotification.NotificationReceiver` 收到广播
3. 调 `AudioPlaybackController`
4. `AudioPlaybackController` 发命令给 `AudioPlaybackService`
5. `AudioPlaybackService` 调 `AudioPlaybackRuntime`
6. `AudioPlaybackRuntime` 驱动当前音频会话

## 6. 音频焦点链路

1. 音频 runtime 播放前请求焦点
2. 系统回调音频焦点变化
3. runtime 根据焦点变化执行 `pause()` 或 `resume()`

## 7. 关键分流原则

- 音频页和列表页只操作 `AudioPlaybackRuntime`
- 视频页只操作 `VideoPlaybackRuntime`
- 音频和视频不共享同一个会话队列
