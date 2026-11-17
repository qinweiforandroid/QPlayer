package com.qw.player.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.qw.player.core.live.ChannelCategory
import com.qw.player.core.live.TvChannel
import com.qw.player.core.live.LiveChannelRepository
import com.qw.player.tv.data.VodRepository

/** 行首尾的横向安全区，对齐官方 TV 指南的 48dp。 */
private val ROW_SIDE_PADDING = 48.dp

/**
 * 卡片聚焦放大 1.1 倍，会向四周溢出约 (0.1 × 卡片尺寸 / 2)。
 * 给每一行留出这段垂直余量，否则上下边缘会被 LazyRow 裁掉。
 */
private val FOCUS_OVERFLOW_PADDING = 12.dp

/**
 * 首页。
 *
 * 按官方 TV 设计指南组织：48dp 横向安全区、行标题 + 横向滚动卡片行、
 * 卡片聚焦时放大 1.1 倍并加发丝边框，保证 3 米外可辨认。
 */
@Composable
fun HomeScreen(
    onChannelSelected: (String) -> Unit,
    onEpisodeSelected: (String) -> Unit
) {
    val channels = remember { LiveChannelRepository.loadChannels() }
    val playlist = remember { VodRepository.loadPlaylist() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15161F), MaterialTheme.colorScheme.background)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 横向 padding 不能加在这里：LazyRow 的裁剪边界会跟着内缩，
            // 聚焦放大的首尾卡片就被切掉。改成每行自己用 contentPadding 让位。
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Box(modifier = Modifier.padding(horizontal = ROW_SIDE_PADDING)) {
                    HomeHeader()
                }
            }

            item {
                Column {
                    Box(modifier = Modifier.padding(horizontal = ROW_SIDE_PADDING)) {
                        SectionTitle("点播")
                    }
                    LazyRow(
                        contentPadding = PaddingValues(
                            horizontal = ROW_SIDE_PADDING,
                            vertical = FOCUS_OVERFLOW_PADDING
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlist.episodes, key = { it.id }) { episode ->
                            val isFirst = episode.id == playlist.episodes.firstOrNull()?.id
                            VodCard(
                                title = episode.title,
                                subtitle = playlist.title,
                                focusRequester = if (isFirst) firstFocus else null,
                                onClick = { onEpisodeSelected(episode.id) }
                            )
                        }
                    }
                }
            }

            ChannelCategory.entries.forEach { category ->
                val list = channels.filter { it.category == category }
                if (list.isNotEmpty()) {
                    item {
                        Column {
                            Box(modifier = Modifier.padding(horizontal = ROW_SIDE_PADDING)) {
                                SectionTitle(category.displayName, count = list.size)
                            }
                            ChannelRow(list, onChannelSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column {
        Text(
            text = "QPlayer TV",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "播放内核示例 · 直播频道 / 点播",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count 个频道",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelRow(channels: List<TvChannel>, onChannelSelected: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(
            horizontal = ROW_SIDE_PADDING,
            vertical = FOCUS_OVERFLOW_PADDING
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(channel = channel, onClick = { onChannelSelected(channel.id) })
        }
    }
}

@Composable
private fun ChannelCard(channel: TvChannel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 200.dp, height = 132.dp),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        )),
        glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(
            elevationColor = MaterialTheme.colorScheme.primary,
            elevation = 12.dp
        ))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ChannelLogo(
                channel = channel,
                modifier = Modifier.size(width = 90.dp, height = 56.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 频道 logo。首字徽标始终画在底层，logo 成功加载后才叠上去，
 * 这样弱网、图片挂掉或 JSON 里没有 logoUrl 时都能自然回退到首字。
 */
@Composable
private fun ChannelLogo(
    channel: TvChannel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = channel.badgeText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun VodCard(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val modifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    Card(
        onClick = onClick,
        modifier = modifier.size(width = 280.dp, height = 158.dp),
        shape = CardDefaults.shape(RoundedCornerShape(18.dp)),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border(
            border = androidx.compose.foundation.BorderStroke(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(18.dp)
        )),
        glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(
            elevationColor = MaterialTheme.colorScheme.primary,
            elevation = 12.dp
        ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
