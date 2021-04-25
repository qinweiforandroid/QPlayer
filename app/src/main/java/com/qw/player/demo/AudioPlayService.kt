package com.qw.player.demo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.qw.player.core.IAudioFocus
import com.qw.player.core.IPlayNotification
import com.qw.player.list.OnPlayListListener
import com.qw.player.list.PlayList

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
//        initMediaSession()
        initPlayer()
        initNotification()
        registerCountdownListener()
        //通知数据变更
//        mediaSession.setMetadata()
    }


    private fun initNotification() {
        playNotification = PlayNotification(this)
        playNotification.registerListener()
    }

    private fun notifyNotificationUpdated() {
        playNotification.notifyNotification(this)
    }

    private fun initPlayer() {
        PlayList.injectAudioFocus(object : IAudioFocus {
            override fun requestAudioFocus(): Int {
                return this@AudioPlayService.requestAudioFocus()
            }

            override fun abandonAudioFocus() {
                this@AudioPlayService.abandonAudioFocus()
            }
        })
        PlayList.addOnPlayListListener(playListListener)

    }


    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(baseContext, "AudioPlayService").apply {

            // Enable callbacks from MediaButtons and TransportControls
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY
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
        PlayList.skipToNext(auto)
    }

    private fun skipToPrevious() {
        PlayList.skipToPrevious()
    }

    private fun stop() {
        PlayList.stop()
    }

    private fun pause(resumeOnFocusGain: Boolean) {
        this.resumeOnFocusGain = resumeOnFocusGain
        PlayList.pause()
    }

    private fun resume() {
        play(PlayList.getPos())
    }


    private fun play(position: Int) {
        val requestAudioFocus = requestAudioFocus()
        notifyNotificationUpdated()
        if (requestAudioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            PlayList.play(position)
        }
    }

    private fun seekTo(pos: Long) {
        PlayList.seekTo(pos)
    }

    private lateinit var audioFocusRequest: AudioFocusRequestCompat
    private var resumeOnFocusGain = true
    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (resumeOnFocusGain) {
                        play(PlayList.getPos())
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
                .setAudioAttributes(AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .build())
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


    private val listener: PlayCountdownManager.OnCountdownListener = object : PlayCountdownManager.OnCountdownListener {
        override fun onCountdownCompleted() {
            if (PlayManager.isPlaying()) {
                PlayManager.pause()
            }
        }
    }

    private fun registerCountdownListener() {
        PlayCountdownManager.addOnCountdownListener(listener)
    }

    private fun unRegisterCountdownListener() {
        PlayCountdownManager.removeOnCountdownListener(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        playNotification.unRegisterListener()
        playNotification.cancel()
        unRegisterCountdownListener()
        PlayList.onDestroy()
    }


}