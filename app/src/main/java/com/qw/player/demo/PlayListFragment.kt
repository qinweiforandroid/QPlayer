package com.qw.player.demo

import android.os.Bundle
import android.view.*
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import com.bumptech.glide.Glide
import com.qw.framework.ui.BaseListV2Fragment
import com.qw.player.demo.databinding.AudioItemLayoutBinding
import com.qw.player.demo.widget.MusicView
import com.qw.player.list.mode.IPlayMode
import com.qw.player.list.OnPlayListListener
import com.qw.player.list.IPod
import com.qw.player.list.Pod
import com.qw.widget.list.BaseViewHolder

class PlayListFragment : BaseListV2Fragment<IPod>() {

    private lateinit var mMusicView: MusicView

    override fun getCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.play_list_fragment, container, false)
    }

    override fun initView(v: View) {
        super.initView(v)
        mPullRecyclerView.setEnablePullToStart(false)
        mPullRecyclerView.setEnablePullToEnd(false)
        mMusicView = findViewById<MusicView>(R.id.mMusicView)
        mMusicView.setOnMusicPlayStateClickListener(View.OnClickListener {
            if (PlayManager.isConnecting()) {
                Toast.makeText(requireContext(), "加载中...", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            PlayManager.play()
        })
        mMusicView.setOnSeekChangedListener(object : MusicView.OnSeekChangedListener {
            override fun onSeekChanged(seekBar: SeekBar) {
                PlayManager.seekTo(seekBar.progress.toLong())
            }
        })
        findViewById<RadioGroup>(R.id.mMusicPlayModeRG).let {
            it.setOnCheckedChangeListener { group, checkedId ->
                setPlayMode(checkedId)
            }
            //显示当前的播放模式
            when (PlayManager.getPlayMode()) {
                IPlayMode.PLAY_MODEL_SINGLE_LOOP -> {
                    it.check(R.id.mMusicPlayModeSingLoopRB)
                }
                IPlayMode.PLAY_MODEL_LIST_LOOP -> {
                    it.check(R.id.mMusicPlayModeListLoopRB)
                }
                IPlayMode.PLAY_MODEL_RANDOM -> {
                    it.check(R.id.mMusicPlayModeRandomRB)
                }
                else -> {

                }
            }
        }

    }

    private fun setPlayMode(checkedId: Int) {
        when (checkedId) {
            R.id.mMusicPlayModeSingLoopRB -> {
                PlayManager.setPlayMode(IPlayMode.PLAY_MODEL_SINGLE_LOOP)
            }
            R.id.mMusicPlayModeListLoopRB -> {
                PlayManager.setPlayMode(IPlayMode.PLAY_MODEL_LIST_LOOP)
            }
            R.id.mMusicPlayModeRandomRB -> {
                PlayManager.setPlayMode(IPlayMode.PLAY_MODEL_RANDOM)
            }
        }
    }

    override fun onCreateItemView(parent: ViewGroup?, viewType: Int): BaseViewHolder {
        return Holder(LayoutInflater.from(requireContext()).inflate(R.layout.audio_item_layout, parent, false))
    }

    inner class Holder(itemView: View) : BaseViewHolder(itemView) {
        private val bind = AudioItemLayoutBinding.bind(itemView)
        private lateinit var pod: IPod

        init {
            bind.mMusicStateImg.setOnClickListener {
                PlayManager.setPlayList(modules)
                val position = modules.indexOf(pod)
                PlayManager.play(position)
            }
        }

        override fun bindData(position: Int) {
            modules[position].let {
                pod = it
                Glide.with(itemView).load(it.getPodCover()).into(bind.mMusicCoverImg)
                bind.mMusicTitleLabel.text = it.getPodTitle()
                bind.mMusicAuthorLabel.text = it.getPodAuthor()
                if (PlayManager.isPlaying() && PlayManager.getPos() == position) {
                    bind.mMusicStateImg.setImageResource(R.drawable.ic_baseline_pause_24)
                } else {
                    bind.mMusicStateImg.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_play_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.previous -> {
                PlayManager.skipToPrevious()
            }
            R.id.next -> {
                PlayManager.skipToNext()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun initData() {
        setHasOptionsMenu(true)
        //https://music.liuzhijin.cn/  在线资源
        modules.add(Pod().apply {
            id = "123"
            title = "还是很想你"
            author = "周杰伦"
            url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            cover = "https://dss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=558639239,778636013&fm=26&gp=0.jpg"
        })
        modules.add(Pod().apply {
            id = "124"
            title = "再看孤独的风景"
            author = "本兮"
            url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            cover = "https://dss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=1034080536,554682047&fm=26&gp=0.jpg"
        })
        modules.add(Pod().apply {
            id = "125"
            title = "客官不可以"
            author = "徐良"
//            url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            cover = "https://dss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=4205434820,3446918311&fm=26&gp=0.jpg"
        })
        adapter.notifyDataSetChanged()
    }


    private val playListListener = object : OnPlayListListener {
        override fun onPlayConnecting(mCurrPodId: String) {
            super.onPlayConnecting(mCurrPodId)
            notifyPlayUpdated()
        }

        override fun onPlayPaused(mCurrPodId: String) {
            super.onPlayPaused(mCurrPodId)
            notifyPlayUpdated()
        }

        override fun onPlayStart(mCurrPodId: String) {
            super.onPlayStart(mCurrPodId)
            notifyPlayUpdated()
        }

        override fun onPlaySwitched(newId: String, oldId: String) {
            super.onPlaySwitched(newId, oldId)
            notifyPlayUpdated()
        }

        override fun onPlayResumed(mCurrPodId: String) {
            super.onPlayResumed(mCurrPodId)
            notifyPlayUpdated()
        }

        override fun onPlayBufferingUpdated(mCurrPodId: String, percent: Int) {
            super.onPlayBufferingUpdated(mCurrPodId, percent)
            mMusicView.setSecondaryProgress((percent / 100.0 * PlayManager.getDuring()).toInt())
        }

        override fun onPlayProgressUpdated(mCurrPodId: String, cur: Int, total: Int) {
            super.onPlayProgressUpdated(mCurrPodId, cur, total)
            mMusicView.setMax(total)
            mMusicView.setProgress(cur)
        }

        override fun onPlayCompleted(mCurrPodId: String) {
            super.onPlayCompleted(mCurrPodId)
            notifyPlayUpdated()
        }
    }

    private fun notifyPlayUpdated() {
        adapter.notifyDataSetChanged()
        if (PlayManager.isPlaying()) {
            mMusicView.playing()
        } else {
            mMusicView.paused()
        }
        mMusicView.loading(PlayManager.isConnecting())
        PlayManager.getPod()?.let {
            mMusicView.setCover(it.getPodCover())
                    .setName(it.getPodTitle())
                    .setSinger(it.getPodAuthor())
                    .setUrl(it.getPodUrl())
                    .notifyDataChanged()
        }
    }


    override fun onResume() {
        super.onResume()
        PlayManager.addOnPlayListListener(playListListener)
        notifyPlayUpdated()
    }

    override fun onPause() {
        super.onPause()
        PlayManager.removeOnPlayListListener(playListListener)
    }
}