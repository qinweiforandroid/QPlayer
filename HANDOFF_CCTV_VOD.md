# CCTV / 点播改造 — 实现状态与交接 (2026-11-13)

本文件记录本轮改造的完成状态、已在真机验证的结果、已知问题清单，供下一次会话继续处理细节。**完成验收后请删除本文件**（或移入 docs/ai 并更新为正式架构文档）。

## 背景

用户需求：仿 B 站的点播播放（手势交互：双击快进快退、滑动调亮度音量、拖动seek预览、长按倍速等）+ CCTV 直播改造（频道分类、多线路切换、断流重连、上下滑切台、侧边栏选台），不含弹幕。详见会话历史中的完整需求原文。

当前改动未提交 git（working tree 里是未 commit 的修改+新增文件），具体文件列表见 `git status --short`。

## 已完成的实现

### 阶段一：player-core / player-media3 / runtime 能力补齐
- `player-core/.../engine/PlayerEngine.kt`：新增 `setLooping(looping: Boolean)` 接口方法
- `player-core/.../session/DefaultPlaybackSession.kt`：新增 `pendingStartPositionMs` 机制 + `setPendingStartPosition()`，用于切换清晰度/线路时保留播放进度（`consumePendingStartPosition()` 在 `resolveAndPlay` 的两个 `engine.setMedia(...)` 调用处消费）
- `player-media3/.../Media3PlayerEngine.kt`：实现 `setLooping`（`player.repeatMode`），`onPlayerError` 增加 `classifyError()` 按 code 区间归类为 `network`/`source`/`decode`/`unknown`，写入 `PlaybackError.extras[EXTRA_ERROR_KIND]`
- `app/.../runtime/BasePlaybackRuntime.kt`：
  - 构造参数新增 `resumeOnFocusGain: Boolean = true`（字段改名为 `resumeOnFocusGainFlag` 避免与参数同名冲突）
  - `initialize()` 幂等判断从 `appContext != null` 改为 `engine != null`，支持 `reset()` 后重建
  - 新增 `reset()`：释放 engine/session，保留 appContext
  - 新增透传：`setPlaybackSpeed/setVolume/setMuted/setLooping/switchSource(sourceId)`
  - `switchSource` 内部会 `session.setPendingStartPosition(getSnapshot().currentPositionMs)` 保留进度
  - 音频焦点瞬时丢失（`AUDIOFOCUS_LOSS_TRANSIENT*`）时是否自动恢复，改为跟随构造参数 `resumeOnFocusGain`，不再硬编码 true
- `app/.../runtime/VideoPlaybackRuntime.kt`：`manageAudioFocus = true`，`resumeOnFocusGain = false`（视频页要求被占用即暂停且不自动恢复），对外暴露新增方法

### 阶段二：通用手势层（VOD 和 LIVE 共用）
- `app/.../widget/PlayerGestureLayout.kt`（新）：`FrameLayout` 子类，`Mode.VOD` / `Mode.LIVE` 两种模式
  - VOD：单击显隐、双击左右快进快退、长按倍速、水平拖动→seek（**需要外部注入 `durationMs`/`positionMs`**，见已知问题）、垂直拖动左半屏→亮度/右半屏→音量
  - LIVE：禁用双击与seek拖动，垂直拖动→`onChannelSwipe(next: Boolean)`
  - `Listener` 接口方法全部有默认空实现（除 `onSingleTap`），调用方按需覆写
- `app/.../widget/PlayerGestureHints.kt`（新）：手势反馈浮层，`showSeekStep/showSeekPreview/updateSeekPreview/hideSeekPreview/showLevel/hideLevel/showSpeed/hideSpeed/showChannelSwitch/clearAll`
- 新增布局 `widget_player_gesture_hints.xml`
- 新增 drawable：`bg_gesture_bubble` `bg_level_track` `bg_level_fill` `ic_gesture_forward` `ic_gesture_backward` `ic_player_volume` `ic_player_brightness` `ic_player_more` `ic_player_quality` `ic_player_muted` `ic_player_loop` `ic_gesture_check`
- 新增字符串资源：`gesture_*` 系列（见 `strings.xml` 尾部）

### 阶段三：点播播放页（全新 `app/.../vod/` 包）
- `VodModels.kt`：`VodQuality` / `VodEpisode` / `VodPlaylist`
- `VodMediaMapper.kt`：`VodEpisode -> PlayableMedia`，清晰度映射为 `playSources`
- `VodRepository.kt`：**本地 mock 数据**，3 个分P，每P 3档清晰度，全部指向同一个真实可用的示例视频 `https://media.w3.org/2010/05/sintel/trailer.mp4`（已用 curl 验证 HTTP 200 + video/mp4）。接入真实接口时只需替换这个文件。
- `VodPlayerActivity.kt`：封面+居中播放按钮起播、单击显隐控制栏（3秒自动隐藏）、双击快进快退10秒+动画提示、长按2倍速松手恢复、水平拖动seek+预览、左右滑动调亮度/音量+气泡、0.5/0.75/1.0/1.25/1.5/2.0倍速面板、清晰度切换面板（保留播放位置）、静音/循环开关、多P自动连播（`onCurrentMediaChanged` 里同步 `currentEpisodeIndex`/`selectedQualityId`/重建选集UI）、竖屏视频全屏保持竖屏铺满（`isPortraitVideo()` 判断 + `requestedOrientation`）、网络错误/加载失败分别提示+重试按钮、`onStop` 暂停不自动恢复、`onDestroy` 调 `VideoPlaybackRuntime.stop()`彻底停止
- `app/.../widget/VodPlayerControlView.kt`（新）：顶部返回/标题/清晰度入口/更多，底部播放暂停/时间/进度条/全屏；清晰度和更多（倍速+静音+循环）以内嵌面板展示
- 新增布局：`activity_vod_player.xml`、`widget_vod_player_controls.xml`
- 新增 drawable：`bg_player_panel` `bg_quality_option(_selected)` `bg_speed_option(_selected)` `bg_vod_cover_overlay` `bg_play_big_button`
- Manifest 注册 `.vod.VodPlayerActivity`（`configChanges` 含 orientation，旋转不重建）
- `MainActivity` 新增 "VodPlayer" 入口按钮

### 阶段四：直播改造（`app/.../cctv/` 包，大改）
- `TvChannel.kt`：重写为 `ChannelCategory`(CCTV/SATELLITE/LOCAL) + `TvLine`(id/label/url) + `EpgEntry` + `TvChannel`(id/name/category/logoUrl/lines/epg)，`badgeText`/`currentEpg(nowMs)`
- `TvChannelRepository.kt`：**重写**，内置 38 个频道（17个CCTV + 15个卫视 + 6个地方台），每个频道 2-3 条线路。**所有线路 URL 均已用 curl 实测校验**（不仅 HTTP 200，还解析了 m3u8 内容确认有 `#EXTINF`/`.ts` 分片且分片可下载）——详见本次会话中段的校验过程。`byCategory()`/`findById()`/`indexOf()` 供页面查询
- `LivePlaybackCoordinator.kt`（新）：直播播放编排
  - `start(channel, lineIndex)` / `stop()` / `switchLine(lineId)` / `suspendMonitoring()` / `resumeMonitoring()` / `currentState()`
  - 线路失败自动降级到下一条线路（`switchingLine` 标志位做 1.5s 保护窗口，避免旧线路的迟到错误误触发新一轮降级——**这是从最初写的 `suppressErrorHandling` 重构而来，之前的实现有缺陷已修**）
  - 全部线路失败后进入断流重连：指数退避 1s→2s→4s→8s，最多4次，每次重连轮换一条线路，`reconnectWatchdog` 8秒无响应则继续下一次重连
  - `toPlayableMedia(selectedLineId)` 扩展函数：频道→PlayableMedia，线路映射为playSources
- `CctvChannelListActivity.kt`（新）：频道列表页，分类tab + RecyclerView列表，点击进入 `CctvPlayerActivity.intent(context, channelId)`
- `CctvPlayerActivity.kt`：**完全重写**。进入即播放无封面；`PlayerGestureLayout.Mode.LIVE`；上下滑切台（`switchChannelBySwipe`，按 `TvChannelRepository.indexOf` 循环）；`ChannelSidebarView` 侧边栏选台；断流/全部线路失败覆盖层；EPG 定时刷新（60s）；`onStop` 暂停+`coordinator.suspendMonitoring()`，`onStart` 恢复监听但不自动播放；`onDestroy` 彻底 stop
- `app/.../widget/LivePlayerControlView.kt`（新）：**无进度条无时间显示**，只有播放暂停/线路切换/音量/亮度/全屏；线路列表用 `render()` 里的 diff 判断避免每次进度回调都重建（`if (state.lines != lines || state.selectedLineId != selectedLineId)`）
- `app/.../cctv/ChannelSidebarView.kt`（新）：分类tab + RecyclerView，侧滑面板
- 新增布局：`activity_live_channel_list.xml` `item_live_channel.xml` `item_channel_sidebar.xml` `widget_channel_sidebar.xml` `widget_live_player_controls.xml`，**`activity_cctv_player.xml` 结构重写**（外层 FrameLayout + 内层 LinearLayout(播放器容器+详情区) + 侧边栏覆盖整个页面）
- 新增 drawable：`bg_channel_tab(_selected)` `bg_channel_grid_item(_selected)` `bg_sidebar`
- Manifest 新增 `.cctv.CctvChannelListActivity`
- `MainActivity` 的 CCTV 入口改为跳转 `CctvChannelListActivity`

### 清理
删除了未被使用的旧实现：`CctvPlayerVM.kt`、`CctvPlayerControlView.kt`、`VideoPlayerControl.kt`、`VideoPlayerControlView.kt`、对应的旧布局 `widget_cctv_player_controls.xml`、`activity_cctv_player_item.xml`。

### 构建验证
`./gradlew clean :app:assembleDebug :player-core:assemble :player-media3:assemble` 全部 BUILD SUCCESSFUL（仅有预期之外的 deprecation 警告，均为已存在的旧代码如 `AudioAttributesCompat`）。

## 真机验证结果（本次会话，设备 PYC110 / OPPO，Android, adb 已连接）

已验证通过：
1. 主页 3 个入口（AudioPlayer/VideoPlayer/VodPlayer/CCTVPlayer）正常显示
2. 点播页：封面 + 居中播放按钮正常显示（P1·预告片）
3. 点击播放按钮 → 封面消失，视频开始播放，进度条和时间（00:01/00:52）正常刷新
4. **双击右侧快进**：从 00:01 跳到 00:23，手势识别正常 ✅

## 已知问题（下一次会话需要处理）

### P0 - 需要立即修
1. **点播页顶部控制栏与系统状态栏重叠**：截图可见 `<` 返回箭头、标题"P1·预告片"、清晰度"高清1080P"文字都压在系统状态栏（时间/电量/网络图标）下面，完全看不清、也点不到。根因：`activity_vod_player.xml` 里 `mPlayerContainer`（播放器容器）在非全屏态应该让顶部控制栏避开状态栏，但当前实现没有做 insets padding/margin 处理。**注意**：旧版 `CctvPlayerActivity` 曾经用 `ViewCompat.setOnApplyWindowInsetsListener` 处理过这个问题（`view.setPadding(0, topInset, 0, 0)`），新的 `VodPlayerActivity`/`CctvPlayerActivity` 都没有照搬这段逻辑，需要补上。
2. **全屏按钮点击疑似无效**：点击右下角全屏图标按钮（坐标约 (838,495) in 918x2000 截图比例）后，截图显示画面继续播放到 00:49 但没有看到全屏态变化的证据（没有二次确认布局是否真的变了，被用户打断）。需要重新测试并用 `uiautomator dump` 核对 `mFullscreenBtn` 实际点击区域坐标是否与手指点击坐标一致（怀疑是坐标系缩放换算错误导致点偏了，而不是代码逻辑问题——因为截图是 918x2000 显示但设备原始分辨率是 1080x2354，换算比例是 1080/918≈1.177）。

### P1 - 待验证（因会话中断未测）
3. 长按 2 倍速、松手恢复
4. 左/右半屏垂直滑动调亮度/音量
5. 水平拖动 seek + 预览气泡
6. 清晰度切换面板、倍速面板、静音/循环开关
7. 多P自动连播、选集点击切换
8. 竖屏视频全屏铺满（当前 mock 视频 Sintel trailer 是横屏16:9，没有竖屏素材可测这条，可能需要找一个竖屏测试视频或接受这条逻辑只做代码审查不做真机验证）
9. 直播页全部交互：频道列表→进入播放、上下滑切台、侧边栏选台、线路切换、断流重连提示、EPG（当前仓库没有真实EPG数据源，`epg` 字段始终为空列表，UI 应该会一直隐藏EPG行，这个属于预期行为不是bug）
10. 通用生命周期：锁屏/切后台暂停、来电占用音频暂停、退出页面无残留声音

### P2 - 代码层面已知但未处理的细节
11. `PlayerGestureLayout` 的 `durationMs`/`positionMs` 依赖外部页面每次 progress 回调主动注入（`VodPlayerActivity.syncGestureContext(snapshot)` 里设置），如果某个页面忘记调用这个同步，seek 手势会用过期的时长计算导致跳转不准。直播页 `CctvPlayerActivity` 目前**没有**注入这两个值（因为直播模式用不到 seek），这个是对的，不用改。
12. `LivePlaybackCoordinator` 的重连逻辑比较复杂（指数退避+线路轮询+看门狗三层嵌套），建议下次会话专门针对断流场景做一次代码走查，确认没有遗漏的状态复位（例如用户在重连过程中手动切频道，`reconnectWatchdog` 的 postDelayed 回调是否会在错误的时机触发——快速扫了一眼 `stop()`/`switchLine()` 似乎没有 `handler.removeCallbacks(reconnectWatchdog)`，可能有泄漏的 watchdog 回调在频道切换后仍然触发，需要确认）。

## 建议下一步

1. 先修 P0 的两个问题（状态栏遮挡、全屏按钮坐标验证）
2. 补上 `LivePlaybackCoordinator.stop()` 和 `switchLine()` 里对 `reconnectWatchdog` 的 `removeCallbacks`（见 P2 第12条）
3. 然后按 P1 列表逐项在真机上点一遍，尤其是手势类交互（双击、长按、滑动）最容易有坐标/阈值计算错误
4. 直播页因为没有真机截图验证过，风险最高，建议优先测
5. 全部跑通后可以把本文件删除或精简后归档到 `docs/ai/`
