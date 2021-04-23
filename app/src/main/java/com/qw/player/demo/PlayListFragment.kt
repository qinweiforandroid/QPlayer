package com.qw.player.demo

import android.os.Bundle
import android.view.*
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import com.bumptech.glide.Glide
import com.qw.framework.ui.BaseListV2Fragment
import com.qw.player.core.IPlayMode
import com.qw.player.core.mode.IPod
import com.qw.player.demo.databinding.AudioItemLayoutBinding
import com.qw.player.demo.widget.MusicView
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
            if (PlayList.isConnecting()) {
                Toast.makeText(requireContext(), "加载中...", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            PlayList.play()
        })
        mMusicView.setOnSeekChangedListener(object : MusicView.OnSeekChangedListener {
            override fun onSeekChanged(seekBar: SeekBar) {
                PlayList.seekTo(seekBar.progress.toLong())
            }
        })
        findViewById<RadioGroup>(R.id.mMusicPlayModeRG).let {
            it.setOnCheckedChangeListener { group, checkedId ->
                setPlayMode(checkedId)
            }
            //显示当前的播放模式
            when (PlayList.getPlayMode()) {
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
                PlayList.setPlayMode(IPlayMode.PLAY_MODEL_SINGLE_LOOP)
            }
            R.id.mMusicPlayModeListLoopRB -> {
                PlayList.setPlayMode(IPlayMode.PLAY_MODEL_LIST_LOOP)
            }
            R.id.mMusicPlayModeRandomRB -> {
                PlayList.setPlayMode(IPlayMode.PLAY_MODEL_RANDOM)
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
                Glide.with(itemView).load(it.getCover()).into(bind.mMusicCoverImg)
                bind.mMusicTitleLabel.text = it.getTitle()
                bind.mMusicAuthorLabel.text = it.getAuthor()
                if (PlayList.isPlaying() && PlayList.getPos() == position) {
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
                PlayList.skipToPrevious()
            }
            R.id.next -> {
                PlayList.skipToNext()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun initData() {
        setHasOptionsMenu(true)
        modules.add(Pod().apply {
            _id = "123"
            _title = "还是很想你"
            _author = "周杰伦"
            _url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            _cover = "https://dss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=558639239,778636013&fm=26&gp=0.jpg"
        })
        modules.add(Pod().apply {
            _id = "124"
            _title = "再看孤独的风景"
            _author = "本兮"
            _url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            _cover = "https://dss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=1034080536,554682047&fm=26&gp=0.jpg"
        })
        modules.add(Pod().apply {
            _id = "125"
            _title = "客官不可以"
            _author = "徐良"
            _url = "http://mpge.5nd.com/2016/2016-3-18/71210/1.mp3"
            _cover = "https://dss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=4205434820,3446918311&fm=26&gp=0.jpg"
        })
        adapter.notifyDataSetChanged()
    }

    class Pod : IPod {
        var _id = ""
        var _title = ""
        var _cover = ""
        var _author = ""
        var _url = ""

        override fun getId(): String {
            return _id
        }

        override fun getTitle(): String {
            return _title
        }

        override fun getCover(): String {
            return _cover
        }

        override fun getAuthor(): String {
            return _author
        }

        override fun getURL(): String {
            return _url
        }
    }

    private val playListListener = object : PlayList.OnPlayListListener {
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
            mMusicView.setSecondaryProgress((percent / 100.0 * PlayList.getDuring()).toInt())
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
        if (PlayList.isPlaying()) {
            mMusicView.playing()
        } else {
            mMusicView.paused()
        }
        mMusicView.loading(PlayList.isConnecting())
        PlayList.getPod()?.let {
            mMusicView.setCover(it.getCover())
                    .setName(it.getTitle())
                    .setSinger(it.getAuthor())
                    .setUrl(it.getURL())
                    .notifyDataChanged()
        }
    }


    override fun onResume() {
        super.onResume()
        PlayList.addOnPlayListListener(playListListener)
        notifyPlayUpdated()
    }

    override fun onPause() {
        super.onPause()
        PlayList.removeOnPlayListListener(playListListener)
    }
}