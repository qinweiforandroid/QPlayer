package com.qw.player.demo.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.qw.player.core.IAudioFocus
import com.qw.player.core.PlayLog
import com.qw.player.demo.*
import com.qw.player.list.OnPlayListListener
import com.qw.player.list.PlayListManager

class AudioPlayService : Service() {
    companion object {
        const val KEY_ACTION = "key_action"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME: String = "action_resume"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val KEY_POSITION = "key_position"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var playNotification: IPlayNotification
    private val playListListener = object : OnPlayListListener {
        override fun onPlayPaused(mCurrPodId: String) {
            super.onPlayPaused(mCurrPodId)
            notifyNotificationUpdated()
        }

        override fun onPlayStart(mCurrPodId: String) {
            super.onPlayStart(mCurrPodId)
            notifyNotificationUpdated()
        }

        override fun onPlaySwitched(newId: String, oldId: String) {
            super.onPlaySwitched(newId, oldId)
            notifyNotificationUpdated()
        }

        override fun onPlayResumed(mCurrPodId: String) {
            super.onPlayResumed(mCurrPodId)
            notifyNotificationUpdated()
        }

        override fun onPlayCompleted(mCurrPodId: String) {
            super.onPlayCompleted(mCurrPodId)
            skipToNext(true)
        }
    }


    override fun onCreate() {
        super.onCreate()
        log("onCreate")
//        initMediaSession()
        PlayListManager.injectAudioFocus(object : IAudioFocus {
            override fun requestAudioFocus(): Int {
                return this@AudioPlayService.requestAudioFocus()
            }

            override fun abandonAudioFocus() {
                this@AudioPlayService.abandonAudioFocus()
            }
        })
        PlayListManager.addOnPlayListListener(playListListener)

        playNotification = PlayNotification(this)
        playNotification.registerListener()
        PlayCountdownManager.addOnCountdownListener(
                listener
        )
        //通知数据变更
//        mediaSession.setMetadata()
    }


    private val handler = Handler(Looper.myLooper()!!)
    private fun notifyNotificationUpdated(bitmap: Bitmap? = null) {
        val pod = PlayManager.getPod() ?: return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            playNotification.notifyNotification(
                    IPlayNotification.PlayEntity.Builder()
                            .setDefaultIcon(R.drawable.ic_launcher_background)
                            .setIcon(bitmap)
                            .setTitle(pod.getPodTitle())
                            .setPlaying(PlayManager.isPlaying())
                            .setSubTitle(pod.getPodAuthor())
                            .builder(), this
            )
        }, 100)
        if (bitmap == null) {
            Glide.with(this@AudioPlayService)
                    .asBitmap()
                    .load(pod.getPodCover())
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap>?
                        ) {
                            notifyNotificationUpdated(resource)
                        }
                    })
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(baseContext, "AudioPlayService").apply {

            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                            or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                    .setActions(
                            PlaybackStateCompat.ACTION_PLAY
                                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                    or PlaybackStateCompat.ACTION_PAUSE
                                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                    or PlaybackStateCompat.ACTION_SEEK_TO
                    )
            setPlaybackState(stateBuilder.build())

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    skipToPrevious()
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    skipToNext()
                }

                override fun onPlay() {
                    super.onPlay()
                    resume()
                }

                override fun onPause() {
                    super.onPause()
                    pause(false)
                }

                override fun onStop() {
                    super.onStop()
                    stop()
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    seekTo(pos)
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.getStringExtra(KEY_ACTION)) {
                ACTION_PREVIOUS -> {
                    skipToPrevious()
                }
                ACTION_NEXT -> {
                    skipToNext()
                }
                ACTION_PLAY -> {
                    val position = intent.getIntExtra(KEY_POSITION, 0)
                    play(position)
                }
                ACTION_PAUSE -> {
                    pause(false)
                }
                ACTION_RESUME -> {
                    resume()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun skipToNext(auto: Boolean = false) {
        log("skipToNext auto:$auto")
        PlayListManager.skipToNext(auto)
    }

    private fun skipToPrevious() {
        log("skipToPrevious")
        PlayListManager.skipToPrevious()
    }

    private fun stop() {
        log("stop")
        PlayListManager.stop()
    }

    private fun pause(resumeOnFocusGain: Boolean) {
        log("pause resumeOnFocusGain:$resumeOnFocusGain")
        this.resumeOnFocusGain = resumeOnFocusGain
        PlayListManager.pause()
    }

    private fun resume() {
        log("resume")
        play(PlayListManager.getPos())
    }


    private fun play(position: Int) {
        log("play position:$position")
        val requestAudioFocus = requestAudioFocus()
        notifyNotificationUpdated()
        if (requestAudioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            PlayListManager.play(position)
        }
    }

    private fun seekTo(pos: Long) {
        PlayListManager.seekTo(pos)
    }

    private lateinit var audioFocusRequest: AudioFocusRequestCompat
    private var resumeOnFocusGain = true
    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
            object : AudioManager.OnAudioFocusChangeListener {
                override fun onAudioFocusChange(focusChange: Int) {
                    log("onAudioFocusChange focusChange:$focusChange")
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (resumeOnFocusGain) {
                                play(PlayListManager.getPos())
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            pause(false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            pause(true)
                        }
                    }
                }
            }

    private fun requestAudioFocus(): Int {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                        AudioAttributesCompat.Builder()
                                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                                .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        return AudioManagerCompat.requestAudioFocus(am, audioFocusRequest)
    }

    private fun abandonAudioFocus() {
        if (this::audioFocusRequest.isInitialized) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(am, audioFocusRequest)
        }
    }

    private val listener: PlayCountdownManager.OnCountdownListener = object :
            PlayCountdownManager.OnCountdownListener {
        override fun onCountdownCompleted() {
            if (PlayManager.isPlaying()) {
                PlayManager.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
        playNotification.unRegisterListener()
        abandonAudioFocus()
        PlayCountdownManager.removeOnCountdownListener(listener)
    }

    private fun log(msg: String) {
        PlayLog.d("service $msg")
    }
}