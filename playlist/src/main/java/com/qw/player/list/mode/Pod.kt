package com.qw.player.list.mode

import java.io.Serializable

class Pod:IPod,Serializable {
    var id:String=""
    var title=""
    var author=""
    var cover=""
    var url=""

    override fun getPodId(): String {
        return id
    }

    override fun getPodTitle(): String {
        return title
    }

    override fun getPodAuthor(): String {
        return author
    }

    override fun getPodCover(): String {
        return cover
    }

    override fun getPodUrl(): String {
        return url
    }

    override fun setPodUrl(url: String) {
        this.url=url
    }
}