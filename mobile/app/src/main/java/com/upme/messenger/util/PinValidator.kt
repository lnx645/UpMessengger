package com.upme.messenger.util

object PinValidator {
  private const val HEX = "0123456789ABCDEF"
  fun isValid(pin: String): Boolean {
    val raw = pin.trim().uppercase().replace(" ", "")
    return raw.length == 8 && raw.all { it in HEX }
  }
  fun normalize(pin: String): String = pin.trim().uppercase().replace(" ", "")
  fun pretty(pin: String): String = normalize(pin).chunked(4).joinToString(" ")
  fun generate(): String = (1..8).map { HEX.random() }.joinToString("")
}
