package com.qw.player.list

interface IPod {
    fun getPodId(): String {
        return ""
    }

    fun getPodTitle(): String {
        return ""
    }

    fun getPodAuthor(): String {
        return ""
    }
    fun getPodCover(): String {
        return ""
    }

    fun getPodUrl(): String {
        return ""
    }
    fun setPodUrl(url:String)
}