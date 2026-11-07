# UpMessenger Architecture (Production)

- App: UpMessenger (UpME), Android Native Kotlin + XML + Material3, Clean Architecture.
- Backend: Elixir, Clean Architecture, persistent TCP + TLS 1.3.
- E2EE 1-1: Signal (X3DH + Double Ratchet) di client. Server direktori prekey + relay opaque.
- Grup: MLS RFC 9420 di client (OpenMLS via Rust NIF/FFI). Server Delivery + Auth Service, jaga epoch order.
- Storage: Local-First. Room/SQLCipher sumber kebenaran. Server antrean sementara sampai ACK, lalu hapus.

Alur kirim: tulis lokal pending -> kirim TCP MSG_SEND -> server ACK -> server forward MSG_RECV -> penerima ACK -> pengirim terima R.
