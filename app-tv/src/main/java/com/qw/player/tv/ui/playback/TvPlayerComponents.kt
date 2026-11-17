package com.qw.player.tv.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Locale

/** 官方 TV 指南的安全边距，避免过扫描把 UI 切掉。 */
val OverscanHorizontal = 48.dp
val OverscanVertical = 27.dp

/** 快进快退步长。 */
const val SEEK_STEP_MS = 10_000L

/**
 * 控制层显隐状态。
 *
 * [interaction] 每次用户操作自增，驱动自动隐藏计时重新开始；
 * 只改 [visible] 不会重置计时，避免“展示→立刻隐藏”的抖动。
 */
@Stable
class ControlVisibility(initiallyVisible: Boolean = true) {
    var visible by mutableStateOf(initiallyVisible)
        private set

    /** 每次有效交互自增，驱动自动隐藏计时重新开始。 */
    var interaction by mutableIntStateOf(0)
        private set

    fun show() {
        visible = true
        interaction++
    }

    fun hide() {
        visible = false
    }

    fun toggle() {
        if (visible) hide() else show()
    }

    fun keepAlive() {
        if (visible) interaction++
    }
}

/**
 * 无操作自动隐藏控制层。
 *
 * 暂停时停留更久，让用户看清暂停画面的信息再收起。
 */
@Composable
fun AutoHideControls(
    visibility: ControlVisibility,
    isPlaying: Boolean,
    autoHideDelayMs: Long = 4_000L,
    pausedDelayMs: Long = 10_000L
) {
    LaunchedEffect(visibility.visible, visibility.interaction, isPlaying) {
        if (!visibility.visible) {
            return@LaunchedEffect
        }
        delay(if (isPlaying) autoHideDelayMs else pausedDelayMs)
        visibility.hide()
    }
}

@Composable
fun rememberControlVisibility(initiallyVisible: Boolean = true): ControlVisibility =
    remember { ControlVisibility(initiallyVisible) }

/**
 * 等节点挂上之后再请求焦点。
 *
 * AnimatedVisibility 里的按钮在同一帧内可能还没 attach 到焦点树，
 * 直接 requestFocus 会抛 "FocusRequester is not initialized"。
 * [key] 变化时会重新尝试一次。
 */
@Composable
fun RequestFocusWhenReady(focusRequester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(focusRequester, key) {
        repeat(6) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(32)
        }
    }
}

/** 毫秒转 `mm:ss` / `h:mm:ss`。 */
fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 播放器里的加载指示器：tv-material 没有进度组件，这里自绘一个无限旋转的弧。 */
@Composable
fun TvBufferingIndicator(modifier: Modifier = Modifier, diameter: Int = 56) {
    val transition = rememberInfiniteTransition(label = "buffering")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = LinearEasing)),
        label = "bufferingAngle"
    )
    Canvas(modifier = modifier.size(diameter.dp)) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = Color.White.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )
        drawArc(
            color = Color.White,
            startAngle = angle,
            sweepAngle = 100f,
            useCenter = false,
            style = stroke
        )
    }
}

/**
 * 进度条。
 *
 * 自身可聚焦：拿到焦点后左右键逐步 seek，每按一次立即生效并显示目标时间。
 * 直播（[durationMs] 为 0）时不绘制进度，只显示「直播」标记。
 */
@Composable
fun PlayerProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val displayPosition = if (safeDuration > 0) {
        (scrubTarget ?: positionMs).coerceIn(0L, safeDuration)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val accent = MaterialTheme.colorScheme.primary
    val progress = if (safeDuration > 0) displayPosition.toFloat() / safeDuration else 0f
    val buffered = if (safeDuration > 0) {
        (bufferedMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 拖动预览在一段时间没有按键后回到真实进度。
    LaunchedEffect(scrubTarget) {
        if (scrubTarget != null) {
            delay(1_200L)
            scrubTarget = null
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (safeDuration > 0) formatPlaybackTime(displayPosition) else "直播",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            if (safeDuration > 0) {
                Text(
                    text = formatPlaybackTime(safeDuration),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onFocusChanged { focused = it.hasFocus }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || safeDuration <= 0) {
                        return@onPreviewKeyEvent false
                    }
                    val base = scrubTarget ?: positionMs
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val delta = if (event.key == Key.DirectionLeft) -SEEK_STEP_MS else SEEK_STEP_MS
                            val target = (base + delta).coerceIn(0L, safeDuration)
                            scrubTarget = target
                            onSeek(target)
                            onInteraction()
                            true
                        }

                        else -> false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barHeight = if (focused) 8.dp.toPx() else 5.dp.toPx()
                val centerY = size.height / 2f
                val radius = CornerRadius(barHeight / 2f)
                val top = centerY - barHeight / 2f

                drawRoundRect(
                    color = Color.White.copy(alpha = if (focused) 0.3f else 0.2f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, barHeight),
                    cornerRadius = radius
                )
                if (safeDuration > 0) {
                    if (buffered > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.35f),
                            topLeft = Offset(0f, top),
                            size = Size(size.width * buffered, barHeight),
                            cornerRadius = radius
                        )
                    }
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, top),
                        size = Size(size.width * progress, barHeight),
                        cornerRadius = radius
                    )
                    if (focused) {
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(size.width * progress, centerY)
                        )
                    }
                }
            }
        }
    }
}

/** 图标按钮：主操作（播放/暂停）用实心白底，其余用半透明底。 */
@Composable
fun TvPlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val colors = if (primary) {
        IconButtonDefaults.colors(
            containerColor = Color.White,
            contentColor = Color.Black,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    }
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = colors
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** 带文字的胶囊按钮，用于「清晰度 / 选集 / 线路」这类入口。 */
@Composable
fun TvPlayerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1)
    }
}

/** 直播角标。 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text(
            text = "直播",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF3B0907),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 播放页右侧面板：清晰度、选集、线路都在这一层打开。
 *
 * 不用弹窗而是侧滑面板，遥控器焦点在面板内部循环，返回键由页面统一处理关闭。
 */
@Composable
fun TvSideSheet(
    title: String,
    content: @Composable ColumnScope.(FocusRequester) -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // 面板滑入动画期间节点还没 attach，延后几帧再抢焦点。
        repeat(6) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(32)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )
        AnimatedVisibility(
            visible = true,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(200)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(160)) { it } + fadeOut(tween(160))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(520.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 40.dp, vertical = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "返回键关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    content(firstFocus)
                }
            }
        }
    }
}

/** 面板里的分组标题。 */
@Composable
fun SheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/**
 * 面板里的一行选项。
 *
 * 用整行可点击 Surface 而不是 Button：文字可以左对齐、选中项右侧打勾，
 * 遥控器上下移动时焦点块最大，3 米外也能看清位置。
 */
@Composable
fun SheetOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    trailing: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已选中",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 全屏提示层：播放失败、直播全部线路失败、点播播完都用它。
 *
 * 主按钮自动拿焦点，遥控器按确认就能直接重试。
 */
@Composable
fun TvPlayerNoticeOverlay(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(6) {
            if (runCatching { primaryFocus.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(32)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(640.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.focusRequester(primaryFocus),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = primaryLabel, maxLines = 1)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    Button(
                        onClick = onSecondary,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = secondaryLabel, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 顶部提示条，用于「正在重连」这类不需要打断播放的提醒。 */@Composable
fun TvPlayerBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = SurfaceDefaults.colors(containerColor = Color(0xE61A1A21)),
        border = Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f)
            ),
            shape = RoundedCornerShape(999.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvBufferingIndicator(diameter = 18)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
