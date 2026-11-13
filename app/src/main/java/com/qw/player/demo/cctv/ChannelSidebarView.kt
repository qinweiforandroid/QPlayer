package com.qw.player.demo.cctv

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qw.player.demo.R

/**
 * 直播频道侧边栏：按分类快速选台。
 */
class ChannelSidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onChannelSelected(channel: TvChannel)
        fun onDismiss()
    }

    private val dimArea: View
    private val tabs: LinearLayout
    private val list: RecyclerView

    private var listener: Listener? = null
    private var currentCategory = ChannelCategory.CCTV
    private var selectedChannelId: String? = null
    private val adapter = ChannelAdapter()

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_channel_sidebar, this, true)
        dimArea = findViewById(R.id.mSidebarDismissArea)
        tabs = findViewById(R.id.mSidebarTabs)
        list = findViewById(R.id.mSidebarList)

        dimArea.setOnClickListener { listener?.onDismiss() }
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        buildTabs()
        visibility = View.GONE
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun show(channelId: String?) {
        selectedChannelId = channelId
        val channel = channelId?.let { TvChannelRepository.findById(it) }
        if (channel != null) {
            currentCategory = channel.category
            buildTabs()
        }
        refreshList()
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(160L).start()
    }

    fun hide() {
        animate().alpha(0f).setDuration(140L).withEndAction {
            visibility = View.GONE
        }.start()
    }

    fun isSidebarShown(): Boolean = visibility == View.VISIBLE

    private fun buildTabs() {
        tabs.removeAllViews()
        ChannelCategory.entries.forEach { category ->
            val item = TextView(context).apply {
                text = category.displayName
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                val h = dp(32)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h).apply {
                    marginEnd = dp(8)
                }
                setPadding(dp(16), 0, dp(16), 0)
                setBackgroundResource(
                    if (category == currentCategory) {
                        R.drawable.bg_channel_tab_selected
                    } else {
                        R.drawable.bg_channel_tab
                    }
                )
                setTextColor(
                    context.getColor(
                        if (category == currentCategory) android.R.color.white else R.color.qp_text_primary
                    )
                )
                setOnClickListener {
                    currentCategory = category
                    buildTabs()
                    refreshList()
                }
            }
            tabs.addView(item)
        }
    }

    private fun refreshList() {
        adapter.submit(TvChannelRepository.byCategory(currentCategory), selectedChannelId)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class ChannelAdapter : RecyclerView.Adapter<ChannelHolder>() {
        private var items: List<TvChannel> = emptyList()
        private var selectedId: String? = null

        fun submit(channels: List<TvChannel>, selected: String?) {
            items = channels
            selectedId = selected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel_sidebar, parent, false)
            return ChannelHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ChannelHolder, position: Int) {
            holder.bind(items[position], items[position].id == selectedId)
        }
    }

    private inner class ChannelHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameLabel: TextView = itemView.findViewById(R.id.mSidebarNameLabel)
        private val badge: TextView = itemView.findViewById(R.id.mSidebarBadgeLabel)

        fun bind(channel: TvChannel, selected: Boolean) {
            nameLabel.text = channel.name
            badge.text = channel.badgeText
            nameLabel.setTextColor(
                context.getColor(if (selected) R.color.qp_accent else R.color.white)
            )
            itemView.setBackgroundResource(
                if (selected) R.drawable.bg_quality_option_selected else R.drawable.bg_quality_option
            )
            itemView.setOnClickListener { listener?.onChannelSelected(channel) }
        }
    }
}
