package com.qw.player.demo.playmanager;

import android.content.Context;
import android.net.wifi.WifiManager;


public class WifiLockManager {
    private static WifiLockManager mInstance;
    private WifiManager mWifiManager;
    private WifiManager.WifiLock mWifiLock;

    private WifiLockManager(Context context) {
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public synchronized static WifiLockManager getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new WifiLockManager(context);
        }
        return mInstance;
    }

    /**
     * 申请设备wifi锁
     */
    public void lock() {
        if (mWifiLock == null) {
            mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "WIFI_LOCK");
            mWifiLock.setReferenceCounted(false);
        }
        if (!mWifiLock.isHeld()) {
            mWifiLock.acquire();
        }
    }

    /**
     * 释放设备wifi锁
     */
    public void unLock() {
        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
            mWifiLock = null;
        }
    }
}