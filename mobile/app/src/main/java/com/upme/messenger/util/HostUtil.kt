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
        debuggable && (host == "127.0.0.1" || host == "10.0.2.2" || host == "localhost")

    /** Prod: hanya primary host (TLS). IP privat dev hanya bila debuggable agar tidak bocor ke release. */
    fun resolveHosts(primary: String): List<String> =
        if (isDevHost(primary)) listOf(primary)
        else if (debuggable) (listOf(primary) + listOf("192.168.70.92", "192.168.70.93", "192.168.70.127")).distinct()
        else listOf(primary)
}
