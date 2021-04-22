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
import com.qw.player.media.PodMediaPlayer

class AudioPlayService : Service() {
    companion object {
        const val KEY_ACTION = "key_action"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    override fun onCreate() {
        super.onCreate()
        initMediaSession()
        initPlayer()
        //通知数据变更
//        mediaSession.setMetadata()
    }

    private fun initPlayer() {
        PlayList.initPlayer(PodMediaPlayer(this))
        PlayList.setAudioFocus(object : IAudioFocus {
            override fun requestAudioFocus(): Int {
                return this@AudioPlayService.requestAudioFocus()
            }

            override fun abandonAudioFocus() {
                this@AudioPlayService.abandonAudioFocus()
            }
        })
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
                    play()
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
        return super.onStartCommand(intent, flags, startId)
    }

    private fun skipToNext() {
        PlayList.skipToNext()
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


    private fun play() {
        val requestAudioFocus = requestAudioFocus()
        if (requestAudioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            PlayList.play()
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
                        play()
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

    override fun onDestroy() {
        super.onDestroy()
        PlayList.onDestroy()
    }
}