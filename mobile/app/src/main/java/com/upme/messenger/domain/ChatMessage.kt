package com.upme.messenger.domain
/** Entity domain murni: tidak tahu Room/TCP. Status mengikuti ala BBM: PENDING->SENT->R. */
data class ChatMessage(val id: String, val conversationId: String, val bodyCipher: ByteArray, val epoch: Int = 0, val status: Status = Status.PENDING, val createdAt: Long = System.currentTimeMillis()) {
  enum class Status { PENDING, SENT, READ }
}
