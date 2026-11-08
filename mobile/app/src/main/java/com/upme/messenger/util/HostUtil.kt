package com.upme.messenger.util

object HostUtil {
  fun isDevHost(host: String): Boolean = host == "127.0.0.1"
  fun resolveHosts(primary: String): List<String> =
    if (isDevHost(primary)) listOf(primary)
    else listOf(primary, "192.168.70.92", "192.168.70.93", "192.168.70.127").distinct()
}
