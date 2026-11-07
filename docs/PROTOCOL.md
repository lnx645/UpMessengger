# UpMessenger TCP Protocol v1 (Production)

- Transport: TCP persistent + TLS 1.3, 1 koneksi per device, heartbeat 30s.
- Framing: [4 byte big-endian length][1 byte type][payload biner].
- Type: 0x01 HELLO, 0x02 AUTH, 0x03 MSG_SEND, 0x04 MSG_ACK, 0x05 MSG_RECV, 0x06 PREKEY_REQ, 0x07 KEYPKG_PUBLISH, 0x08 HEARTBEAT, 0x09 DISCONNECT.
- MSG_SEND payload: message_id(16) + conversation_id + epoch + ciphertext opaque. Server tidak parse ciphertext.
- Resume: session_id + last_offset, server replay yang belum ACK penerima.
- Batas: payload maks 256KB, rate-limit 30 msg/menit per device, queue offline maks 500 msg.
