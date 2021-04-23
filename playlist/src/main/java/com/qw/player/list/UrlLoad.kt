package com.qw.player.list

interface IUrlLoad {
    fun load(id: String, callback: UrlLoadCallback)
}

interface UrlLoadCallback {
    fun onLoadSuccess(url: String)

    fun onLoadFailure(code: Int, msg: String)
}