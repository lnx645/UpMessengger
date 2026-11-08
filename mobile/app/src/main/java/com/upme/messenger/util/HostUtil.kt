package com.upme.messenger.util

import android.content.Context
import android.content.pm.ApplicationInfo

object HostUtil {
    /** Diisi sekali dari UpMeApp.onCreate via applicationInfo — tanpa BuildConfig. */
    @Volatile var debuggable: Boolean = false

    fun init(ctx: Context) {
        debuggable = 0 != (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
    }

    fun isDevHost(host: String): Boolean =
        debuggable && (
            host == "127.0.0.1" || host == "10.0.2.2" || host == "localhost" ||
            host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")
        )

    /** Hotspot debug: primary 127.0.0.1 fallback ke IP host via WiFi agar 2 HP via hotspot bisa, prod tetap TLS */
    fun resolveHosts(primary: String): List<String> {
        val hotspotFallback = listOf("192.168.70.92", "192.168.70.93", "192.168.70.127", "192.168.70.93")
        return if (isDevHost(primary)) {
            if (debuggable) (listOf(primary) + hotspotFallback).distinct() else listOf(primary)
        } else if (debuggable) (listOf(primary) + hotspotFallback).distinct()
        else listOf(primary)
    }
}
