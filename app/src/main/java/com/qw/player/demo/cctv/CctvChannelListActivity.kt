package com.qw.player.demo.cctv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qw.player.core.live.ChannelCategory
import com.qw.player.core.live.LiveChannelRepository
import com.qw.player.core.live.TvChannel
import com.qw.player.demo.R
import com.qw.player.demo.databinding.ActivityLiveChannelListBinding

/**
 * 直播频道列表页：分类 + 频道列表，点击进入直播播放页。
 */
class CctvChannelListActivity : AppCompatActivity() {

    private lateinit var bind: ActivityLiveChannelListBinding
    private val adapter = ChannelAdapter()
    private var currentCategory = ChannelCategory.CCTV

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityLiveChannelListBinding.inflate(layoutInflater)
        setContentView(bind.root)

        bind.mChannelCountLabel.text = getString(
            R.string.live_channel_count,
            LiveChannelRepository.loadChannels().size
        )
        bind.mChannelRecyclerView.layoutManager = LinearLayoutManager(this)
        bind.mChannelRecyclerView.adapter = adapter

        buildTabs()
        refreshList()
    }

    private fun buildTabs() {
        bind.mCategoryTabs.removeAllViews()
        ChannelCategory.entries.forEach { category ->
            val item = TextView(this).apply {
                text = category.displayName
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(34)
                ).apply { marginEnd = dp(10) }
                setPadding(dp(18), 0, dp(18), 0)
                setBackgroundResource(
                    if (category == currentCategory) {
                        R.drawable.bg_channel_tab_selected
                    } else {
                        R.drawable.bg_channel_tab
                    }
                )
                setTextColor(
                    getColor(
                        if (category == currentCategory) android.R.color.white else R.color.qp_text_primary
                    )
                )
                setOnClickListener {
                    currentCategory = category
                    buildTabs()
                    refreshList()
                }
            }
            bind.mCategoryTabs.addView(item)
        }
    }

    private fun refreshList() {
        adapter.submit(LiveChannelRepository.byCategory(currentCategory))
    }

    private fun openChannel(channel: TvChannel) {
        startActivity(CctvPlayerActivity.intent(this, channel.id))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class ChannelAdapter : RecyclerView.Adapter<ChannelHolder>() {
        private var items: List<TvChannel> = emptyList()

        fun submit(channels: List<TvChannel>) {
            items = channels
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_live_channel, parent, false)
            return ChannelHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ChannelHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class ChannelHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.mChannelBadgeLabel)
        private val logoImage: ImageView = itemView.findViewById(R.id.mChannelLogoImage)
        private val name: TextView = itemView.findViewById(R.id.mChannelNameLabel)
        private val meta: TextView = itemView.findViewById(R.id.mChannelMetaLabel)

        fun bind(channel: TvChannel) {
            bindChannelLogo(badge, logoImage, channel)
            name.text = channel.name
            meta.text = getString(R.string.live_channel_meta, channel.lines.size)
            itemView.setOnClickListener { openChannel(channel) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CctvChannelListActivity::class.java)
    }
}
