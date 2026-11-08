# Audit Fitur Pertemanan + Kesiapan Production & Security

Tanggal: 2026-11-07
Cakupan: `mobile/app/src/main/java/.../teman/TemanFragment.kt`, `data/net/TcpClient.kt`, `backend/lib/up_messenger/interface/tcp_handler.ex`, `infrastructure/auth_store.ex`, `domain/jwt.ex`, `AndroidManifest.xml`, `network_security_config.xml`, `UpMeDb.kt`, `CryptoProvider.kt`.

---

## 1. Fitur Pertemanan — Status

### 1.1 Yang sudah beres
- Backend: invite `0x32`, pending list `0x34`, accept `0x35`, reject `0x36`, delete/block `0x37`, search `0x38`, `phone_for_pin 0x39` — semua ada di `tcp_handler.ex` + `auth_store.ex`.
- Client: invite by PIN manual (validasi 8 hex, cegah self-invite, cegah duplikat teman/pending), terima/tolak request, hapus lokal + `deleteTeman(0x37)`, search lokal Room, chip Semua/Pending.

### 1.2 Yang kurang / rusak (jangan perbaiki dulu — dicatat saja)
1. `TcpClient.kt` **tidak punya** `search(0x38)` dan `phoneForPin(0x39)` — `grep 0x38/0x39` kosong.
2. Tambah dari Kontak HP (`pickContact` di `TemanFragment.kt`) stub — cuma toast `PIN lookup via server segera`, tidak panggil `0x39`.
3. Search server mati — komentar `server search 0x38 async` ada tapi implementasi tidak ada; hanya `temanDao().search()` lokal.
4. `syncPendingFromServer()` memanggil `tcp.syncPending()` tapi hasilnya **dibuang**, tidak di-`upsert` ke `PendingDao`. Tab Pending tidak pernah terisi dari server.
5. Tidak ada push/badge untuk invite masuk. Polling 5 detik hanya `refresh()` lokal, bukan sync server. User harus buka chip Pending manual.
6. Blokir disembunyikan — `showTemanOptions()` hanya Chat/Hapus; Blokir hanya via `setOnLongClickListener` di teks message. Tidak ada daftar block, tidak ada unblock.
7. Frame `0x37` hanya handle `block=true → blocked` / `delete`. Tidak ada frame unblock dan tidak ada frame list-blocked (padahal `block_pin/unblock_pin/blocked_for` ada di `auth_store.ex`).
8. Tidak ada cancel outgoing pending (`direction=OUT` tidak dibedakan, `showPendingOnly` campur IN+OUT).
9. Tidak ada rename/edit nama teman, tidak ada lihat profil dari daftar teman.
10. Tombol QR/Scan: `QR Scan belum tersedia — masukkan PIN manual`.
11. Friendship server-side tidak persisten: `invite(auto_approve=true)` langsung `accepted` tanpa simpan relasi, jadi hapus/blokir hanya lokal, tidak propagasi ke sisi lawan.
12. `blocked_for` tidak dicek di `SendMessage` — user diblokir tetap bisa kirim ciphertext (relay buta).
13. Bug kecil: `search(currentFilter.uppercase())` — nama tersimpan campur case, bisa miss; sebaiknya `COLLATE NOCASE` atau simpan kolom normalized.

### 1.3 Saran perbaikan nanti (fitur)
- [ ] Tambah `searchByPin(0x38)` + `phoneForPin(0x39)` di `TcpClient`, sambungkan ke search bar (`>=2 char`) dan `pickContact`.
- [ ] Perbaiki `syncPending()`: simpan hasil server ke `PendingDao`, bedakan IN/OUT, tambah tombol Batal untuk OUT.
- [ ] Keluarkan Blokir jadi tombol ketiga yang jelas + layar Blocked List + unblock via `0x37` baru (atau frame baru `0x3A`).
- [ ] Tambah badge jumlah pending di chip/tab Teman + notifikasi invite masuk.
- [ ] Tambah rename teman + lihat profil + cancel outgoing + QR scan/share PIN.

---

## 2. Security & Standar Production

### 2.1 Kritis — wajib sebelum rilis
| # | Temuan | Lokasi | Dampak | Remediasi |
|---|--------|--------|--------|-----------|
| S1 | JWT fail-open, fallback secret dev | `domain/jwt.ex#secret()` → `"dev_secret_change_in_prod_32bytes_min_32"` | Semua token bisa ditempa jika ENV kosong | `raise` saat `Mix.env()==:prod` dan ENV kosong; gagal start, jangan fallback |
| S2 | DB tidak terenkripsi | `UpMeDb.kt#create()` pakai `Room.databaseBuilder` tanpa passphrase; `SQLCipher 4.5.4` sudah di deps tapi tidak dipakai | Chat + PIN + pending plaintext di disk, bisa dibaca via root/backup | Aktifkan SQLCipher (`SupportFactory` + passphrase dari Android Keystore); migrasi data lama |
| S3 | `fallbackToDestructiveMigration()` | `UpMeDb.kt` | Update app = hapus semua data user | Ganti migrasi eksplisit `MIGRATION_3_4 dst.` |
| S4 | Private key X25519 plaintext | `CryptoProvider.kt:41,78` pakai `getSharedPreferences(PREFS, MODE_PRIVATE)` biasa | Kunci E2EE bisa dicuri, dekripsi history | Pindah ke `EncryptedSharedPreferences` / Keystore; sesuai komentar kode yang sudah klaim AES256_GCM |
| S5 | PIN/token di prefs biasa | `ProfilFragment:33`, `PinFragment:21`, `TemanFragment:312` pakai `getSharedPreferences("upme",0)` untuk PIN | Bocor via backup/root | Hanya lewat `AuthPrefs` (encrypted); hapus read/write langsung, panggil `migrateLegacy()` sekali |
| S6 | Backup tidak dimatikan | `AndroidManifest.xml` tanpa `allowBackup=false` / `fullBackupContent` rules | Token + `upme.db` ikut Google backup / `adb backup` | `android:allowBackup="false"`, `android:fullBackupContent="false"`, `android:dataExtractionRules` untuk API 31+ |
| S7 | Pinning tidak dipakai | `CertPinning.tlsSocketFactory()` ada tapi `TcpClient.kt:18`, `AuthRepository.kt:26`, `PrivacyPrefs.kt:116` pakai `SSLSocketFactory.getDefault()` | MITM pada koneksi chat utama | Pakai `CertPinning.tlsSocketFactory()` untuk host prod; ganti pin placeholder di `network_security_config.xml` dengan hasil `openssl s_client` real |
| S8 | Rate-limit bolong | `tcp_handler.ex` hanya `check_ip` di `0x10/0x11` (OTP) | Spam invite `0x32`, enumerasi PIN (8 hex mudah brute-force), spam `0x03` | Rate-limit per-user + per-IP untuk invite/search/message; tambah captcha/cooldown; log + ban sementara |
| S9 | Permission SMS overkill | Manifest `RECEIVE_SMS + READ_SMS` | Ditolak Play review; bisa baca SMS bank | Migrasi ke SMS Retriever API / User-Consent API, hapus `READ_SMS` |
| S10 | Secret server ikut repo | `backend/erl_crash.dump`, `priv/jwt_keys.json` (RSA 2048 plaintext) | Private key bocor ke git | `git rm --cached`, tambah `.gitignore` (`*.dump`, `priv/*.json`), rotasi key, pindah ke vault/ENV atau file 0600 di luar repo |

### 2.2 Menengah
- [ ] Pastikan `AuditLog` + `Logger` redact PII (jangan log phone/OTP/PIN/token penuh).
- [ ] `JwtHelper.isExpired`: `exp==0` dianggap valid selamanya — harus dianggap expired atau paksa refresh.
- [ ] `network_security_config.xml` masih trust `user CA` untuk IP dev `192.168.70.x` + `debug-overrides` — pastikan hanya di `debug/`, tidak di `release/`.
- [ ] Dual JWT (RS256 + HS256 fallback) — hapus path HS256 dev-secret, samakan ke RS256 + `kid` + `exp` access 15 mnt / refresh 30 hari + revocation list.
- [ ] `hardwareAccelerated=false` + `largeHeap=false` berlawanan dengan target 60fps di `STANDARD.md` — ukur ulang; Coil thumbnail + Paging3 belum penuh (baru `LIMIT 50` tanpa Paging3).
- [ ] Hapus kredensial SMS Gate hardcode/default — hanya via ENV (`SMS_GATE_USERNAME/PASSWORD/DEVICE_ID` sudah benar, pertahankan, jangan tambah default prod).
- [ ] Tambah screenshot protection / app-lock timeout untuk chat + PIN screen; cek root/debug detection sebelum rilis (sesuai kebutuhan risiko).
- [ ] Siapkan Proguard mapping upload + `versionCode` naik tiap rilis; reproducible build offline (`--offline --max-workers=2` sudah sesuai `STANDARD.md`).

### 2.3 Urutan kerja yang disarankan
1. S1 + S10 (secret & key) — paling murah, dampak terbesar.
2. S2 + S3 + S4 + S5 + S6 (data-at-rest & backup).
3. S7 (pinning real) + bersihkan IP dev dari release config.
4. S8 (rate-limit invite/search/message) + anti-enumerasi PIN (tambah delay + error generik, jangan bedakan `not_found` vs `blocked`).
5. S9 (SMS Retriever) + audit log redaction.
6. Baru kembali ke Section 1.3 (fitur teman).

---

## 3. Cara verifikasi nanti
- `rg -n "dev_secret|getSharedPreferences\(\"upme\"|SSLSocketFactory.getDefault|fallbackToDestructive|READ_SMS" mobile backend` harus kosong (kecuali yang disengaja di debug).
- `mix prod` tanpa `UPME_JWT_SECRET` harus gagal start dengan pesan jelas.
- DB di `/data/data/com.upme.messenger/databases/upme.db` tidak bisa dibuka tanpa passphrase (`sqlcipher` CLI / `strings` tidak tampilkan PIN/chat).
- Pinning: intercept dengan Charles/mitmproxy + cert sendiri harus gagal (fail-closed).
- Rate-limit: 20x invite beruntun dari 1 IP/user → `rate_limited`, bukan `accepted/pending`.

---
*File ini hanya catatan audit — belum ada kode yang diubah.*
