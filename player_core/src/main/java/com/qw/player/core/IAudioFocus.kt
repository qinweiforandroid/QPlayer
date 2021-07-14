package com.qw.player.core


interface IAudioFocus {
    fun requestAudioFocus(): Int
    fun abandonAudioFocus()
}