# Audit: Kenapa Kadang Harus Login Kembali

Tanggal: 2026-11-07 — status: audit only, belum ada kode diubah.

## Ringkasan
6 akar masalah: token hilang di RAM, JWT pendek tanpa refresh, PIN dual-source, ETS hilang saat BEAM restart, host mismatch fail-closed, destructive migration.

## Temuan 1–6 (prioritas)

| # | Masalah | Bukti (file:line) | Dampak | Fix di .md (belum eksekusi) |
|---|---------|-------------------|--------|-----------------------------|
| 1 | Token hilang — `EncryptedSharedPreferences` fallback `InMemoryPrefs` RAM-only | `data/auth/AuthPrefs.kt:40-78`, `PrivacyPrefs.kt:40-78` (`catch → InMemoryPrefs`) | Kill app / low-RAM → token/phone/pin hilang → OTP lagi, terlihat random | Pindah ke file-encrypted fallback (bukan RAM), log `MasterKey` fail, fallback `SharedPreferences("upme_enc_fallback")` encrypted-at-rest + warning toast (fail-closed tapi persist) |
| 2 | JWT 15m tanpa refresh_token | `backend/lib/up_messenger/domain/jwt.ex` `exp 900s`, `mobile AuthPrefs.token` single | Lewat 15m + cold start → `frame 0x02` gagal → `MainActivity` redirect Welcome, user merasa tiba-tiba logout | Tambah `refresh_token` 30d (DETS `refresh_tokens`), endpoint `0x1A refresh`, `UpMeApp.onCreate` silent refresh + `WorkManager` 10m sebelum exp |
| 3 | PIN dual-source divergen | `ProfilFragment.kt:38` `SharedPreferences("upme")` + `AuthPrefs.pin`, `TemanFragment:326` `authPrefs.pin ?: prefs("upme")` | Salah satu ter-clear → guard `PIN sendiri` salah, user reset via logout | Single source `AuthPrefs` saja, migrasi one-time copy `upme`→`AuthPrefs` lalu `prefs.delete("pin")` |
| 4 | BEAM restart ETS volatile, DETS tidak reload | `backend/lib/up_messenger/application.ex` tidak `load priv/jwt_keys.json` sebelum accept, `AuthStore` ETS `:upme_jwt_keys/:upme_users` | Deploy/restart server → `kid` unknown → semua device `unauthenticated` → mass logout | `Application.start` wajib `Jwt.loadKeys()`, `AuthStore.loadUsers()`, `Persistence.sync` before `Thousand Island` listen; tambah `GenServer` persist tiap 60s |
| 5 | Host mismatch = dianggap logout | `AuthRepository.frame():16` `HostUtil.isDevHost`, `TcpClient` `IOException` → `Result.failure` diperlakukan sama dengan `401` | `adb reverse` putus / ganti WiFi `192.168.70.x` → `SSLException` → logout palsu | Bedakan `network_error` vs `401`; `network_error` → retry + `AppToast "Offline, coba lagi"` tanpa clear token |
| 6 | Destructive migration hapus DB | `data/local/UpMeDb.kt` `fallbackToDestructiveMigration()` | Bump version 3→4 tanpa migration → `teman/pending` hilang → `ChatsFragment` anggap belum setup → redirect login | Ganti `AutoMigration` + `exportSchema true` + `Migration(3,4)` kosong, hapus `fallbackToDestructive` |

## Urutan eksekusi nanti (setelah teman 100%)
1 → 3 → 4 → 2 → 5 → 6 (dependensi: single-source dulu baru refresh & reload).
