package com.qw.player.demo.audio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.session.RepeatMode
import com.qw.player.demo.R
import com.qw.player.demo.databinding.AudioItemLayoutBinding
import com.qw.player.demo.databinding.PlayListFragmentBinding
import com.qw.player.demo.runtime.AudioPlaybackController
import com.qw.player.demo.runtime.AudioPlaybackRuntime
import com.qw.player.demo.widget.MusicView

class AudioPlayerFragment : Fragment() {
    private lateinit var bind: PlayListFragmentBinding
    private val modules = mutableListOf<PlayableMedia>()
    private val playListAdapter = PlayListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return PlayListFragmentBinding.inflate(inflater, container, false).also {
            bind = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        bind.recyclerView.adapter = playListAdapter
        bind.mMusicView.setOnMusicPlayStateClickListener(
            View.OnClickListener {
                when (AudioPlaybackRuntime.getSnapshot().state) {
                    PlaybackState.PLAYING,
                    PlaybackState.BUFFERING,
                    PlaybackState.PREPARING -> AudioPlaybackController.pause(requireContext())

                    PlaybackState.PAUSED,
                    PlaybackState.READY -> AudioPlaybackController.resume(requireContext())

                    else -> AudioPlaybackController.play(
                        requireContext(),
                        if (AudioPlaybackRuntime.getCurrentIndex() >= 0) {
                            AudioPlaybackRuntime.getCurrentIndex()
                        } else {
                            0
                        }
                    )
                }
            }
        )
        bind.mMusicView.setOnSeekChangedListener(object : MusicView.OnSeekChangedListener {
            override fun onSeekChanged(seekBar: SeekBar) {
                AudioPlaybackRuntime.seekTo(seekBar.progress.toLong())
            }
        })
        bind.mMusicPlayModeRG.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mMusicPlayModeSingLoopRB -> {
                    AudioPlaybackRuntime.updatePlaybackMode(
                        PlaybackMode(
                            repeatMode = RepeatMode.ONE,
                            shuffleEnabled = false
                        )
                    )
                }

                R.id.mMusicPlayModeListLoopRB -> {
                    AudioPlaybackRuntime.updatePlaybackMode(
                        PlaybackMode(
                            repeatMode = RepeatMode.ALL,
                            shuffleEnabled = false
                        )
                    )
                }

                R.id.mMusicPlayModeRandomRB -> {
                    AudioPlaybackRuntime.updatePlaybackMode(
                        PlaybackMode(
                            repeatMode = RepeatMode.ALL,
                            shuffleEnabled = true
                        )
                    )
                }
            }
        }
        bind.mPlayCountdownBtn.visibility = View.GONE
        bind.mPlayCountdownInfoLabel.text = "V2 Session"

        AudioPlaybackRuntime.setQueue(createDemoQueue(), 0)
        AudioPlaybackRuntime.updatePlaybackMode(
            PlaybackMode(
                repeatMode = RepeatMode.ALL,
                shuffleEnabled = false
            )
        )
        AudioPlaybackRuntime.addListener(sessionListener)

        modules.clear()
        modules.addAll(AudioPlaybackRuntime.getQueue())
        playListAdapter.notifyDataSetChanged()
        bind.mMusicPlayModeRG.check(R.id.mMusicPlayModeListLoopRB)
        AudioPlaybackController.play(requireContext(), 0)
    }

    override fun onResume() {
        super.onResume()
        notifySessionUpdated(AudioPlaybackRuntime.getSnapshot())
    }

    override fun onDestroyView() {
        AudioPlaybackRuntime.removeListener(sessionListener)
        bind.recyclerView.adapter = null
        super.onDestroyView()
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_play_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.previous -> AudioPlaybackController.skipToPrevious(requireContext())
            R.id.next -> AudioPlaybackController.skipToNext(requireContext())
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class PlayListAdapter : RecyclerView.Adapter<PlayListViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayListViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.audio_item_layout, parent, false)
            return PlayListViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: PlayListViewHolder, position: Int) {
            holder.bind(modules[position], position)
        }

        override fun getItemCount(): Int = modules.size
    }

    private inner class PlayListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemBinding = AudioItemLayoutBinding.bind(itemView)

        fun bind(media: PlayableMedia, position: Int) {
            itemBinding.mMusicStateImg.setOnClickListener {
                AudioPlaybackController.play(requireContext(), bindingAdapterPosition)
            }
            Glide.with(itemView)
                .load(media.metadata.coverUrl)
                .into(itemBinding.mMusicCoverImg)
            itemBinding.mMusicTitleLabel.text = media.metadata.title
            itemBinding.mMusicAuthorLabel.text = media.metadata.artist
            val isCurrent = AudioPlaybackRuntime.getCurrentIndex() == position
            val state = AudioPlaybackRuntime.getSnapshot().state
            itemBinding.mMusicStateImg.setImageResource(
                if (isCurrent && (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING)) {
                    R.drawable.ic_baseline_pause_24
                } else {
                    R.drawable.ic_baseline_play_arrow_24
                }
            )
        }
    }

    private val sessionListener = object : PlaybackSessionListener {
        override fun onQueueChanged(queue: List<PlayableMedia>, currentIndex: Int) {
            modules.clear()
            modules.addAll(queue)
            playListAdapter.notifyDataSetChanged()
        }

        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            notifySessionUpdated(snapshot)
        }

        override fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {
            notifySessionUpdated(snapshot)
        }

        override fun onPlaybackError(error: PlaybackError, snapshot: PlaybackSnapshot) {
            notifySessionUpdated(snapshot)
            Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifySessionUpdated(snapshot: PlaybackSnapshot) {
        if (!isAdded) {
            return
        }
        playListAdapter.notifyDataSetChanged()
        when (snapshot.state) {
            PlaybackState.PLAYING -> bind.mMusicView.playing()
            else -> bind.mMusicView.paused()
        }
        bind.mMusicView.loading(
            snapshot.state == PlaybackState.BUFFERING || snapshot.state == PlaybackState.PREPARING
        )
        bind.mMusicView.setMax(snapshot.durationMs.toInt().coerceAtLeast(0))
        bind.mMusicView.setProgress(snapshot.currentPositionMs.toInt().coerceAtLeast(0))
        bind.mMusicView.setSecondaryProgress(snapshot.bufferedPositionMs.toInt().coerceAtLeast(0))

        val current = AudioPlaybackRuntime.getCurrentMedia()
        if (current != null) {
            bind.mMusicView.setCover(current.metadata.coverUrl)
                .setName(current.metadata.title)
                .setSinger(current.metadata.artist)
                .setUrl(current.resolveDefaultSource()?.url ?: "")
                .notifyDataChanged()
        }
    }

    private fun createDemoQueue(): List<PlayableMedia> {
        return listOf(
            PlayableMedia(
                mediaId = "v2-list-1",
                mediaType = MediaType.AUDIO,
                metadata = PlayerMediaMetadata(
                    title = "V2 列表音频一",
                    subtitle = "列表播放示例",
                    artist = "QPlayer",
                    coverUrl = "https://dss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=558639239,778636013&fm=26&gp=0.jpg"
                )
            ),
            PlayableMedia(
                mediaId = "v2-list-2",
                mediaType = MediaType.AUDIO,
                metadata = PlayerMediaMetadata(
                    title = "V2 列表音频二",
                    subtitle = "自动切歌示例",
                    artist = "QPlayer",
                    coverUrl = "https://dss1.bdstatic.com/70cFvXSh_Q1YnxGkpoWK1HF6hhy/it/u=1166503026,41947489&fm=26&gp=0.jpg"
                )
            ),
            PlayableMedia(
                mediaId = "v2-list-3",
                mediaType = MediaType.AUDIO,
                metadata = PlayerMediaMetadata(
                    title = "V2 列表音频三",
                    subtitle = "随机 / 循环示例",
                    artist = "QPlayer",
                    coverUrl = "https://dss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=1475331839,2066156315&fm=26&gp=0.jpg"
                )
            )
        )
    }
}
