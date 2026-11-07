package com.upme.messenger.presentation.auth

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.NumberParseException

object PhoneValidator {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    fun isValid(phone: String, defaultRegion: String = "ID"): Boolean {
        val trimmed = phone.trim()
        return try {
            val parsed = util.parse(trimmed, defaultRegion)
            util.isValidNumber(parsed)
        } catch (_: NumberParseException) {
            false
        }
    }

    fun formatE164(phone: String, defaultRegion: String = "ID"): String? {
        return try {
            val parsed = util.parse(phone.trim(), defaultRegion)
            if (util.isValidNumber(parsed)) util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164) else null
        } catch (_: Exception) { null }
    }

    fun normalizeOrOriginal(phone: String, defaultRegion: String = "ID"): String {
        return formatE164(phone, defaultRegion) ?: phone.trim().replace(Regex("[^0-9+]"), "")
    }
}
