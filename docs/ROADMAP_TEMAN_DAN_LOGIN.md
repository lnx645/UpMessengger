# Roadmap: Teman 100% + Fix Login 1–6

> Dokumen rencana — belum ada kode diubah. `go` untuk eksekusi.

## Goal
Teman E2E via PIN/QR/Kontak 100% + login tidak lagi random force-logout.

## Success Criteria
- 2 HP real `133365452A100177` saling invite → B muncul `Permintaan` tanpa restart, Terima → A lihat `Teman` nama benar, hapus/blokir sinkron server.
- Kill app / 24 jam tanpa login ulang (refresh_token + ETS reload).

## Approach
Biarkan backend frames `0x32-0x39` apa adanya, wire mobile yang masih local-only (A1-A5) dulu, lalu betulkan persist login (1-6). Tidak sentuh infra TLS.

## Steps

### Fase 1 — Teman server sync (A1-A5, B1, B4)
- `TemanFragment.syncPending()` simpan `0x34` ke `pendingDao`, badge count, poll 15s fallback + FCM defer.
- `showRequestSheet` 2-HP E2E + `0x38` search debounce + `0x39` phone→PIN + `READ_CONTACTS` perm.
- Avatar pending load file, `AppSearchBar`/`AppEmptyState` reuse.

### Fase 1C — FCM teman baru (diskusi, asumsi Opsi A)
- Prasyarat: `mobile/app/google-services.json` baru untuk `com.upme.messenger` (Add app di project `dachat-6fd87`, 2-menit, file 3421B lama untuk `com.d03x.dapay.dapay` tidak dipakai karena package mismatch). `backend/priv/fcm.json` ← `dachat-6fd87-firebase-adminsdk-ulesc-565f7f2391.json` (chmod 600, `.gitignore`).
- Mobile: `+ firebase-messaging:23.4+`, `MyFirebaseService: FirebaseMessagingService` → `onNewToken()` → `0x3A register_fcm {phone, token_jwt, fcm_token}` ke BE (upsert `users.dets` `phone→fcm_token`, update tiap login/`onNewToken`). `onMessageReceived` → `NotificationChannel "teman"` high + `PendingIntent` deep-link `TemanFragment(showPendingOnly=true)` + action `Terima`/`Tolak` via `WorkManager` hit `0x35/0x36` tanpa buka app. Fallback bila Play Services tidak ada: tetap poll 15s.
- Backend: `0x32 invite` sukses → `AuthStore.getFcmToken(targetPin)` → `FcmPush.send(project_id=dachat-6fd87, serviceAccount=priv/fcm.json)` data `{type:"teman_request", from_pin, from_name, from_phone}`; jika `autoApprove` target true → notif "Kamu & A berteman", else "A ingin berteman — Terima/Tolak". Rate-limit push 1/5s per target.
- Validasi: `adb shell dumpsys notification | grep teman`, invite dari A → B terima notif <3s (tanpa buka app), tap Terima → `temanDao` kedua HP sinkron.

### Fase 2 — Login persist (1-6)
- 1 InMemory→file fallback, 3 single-source PIN, 4 BEAM reload, 2 refresh_token+silent, 5 network vs 401, 6 AutoMigration.

## Validation Plan
- `./gradlew :app:testDebugUnitTest` (PinValidator, TemanForceClose) + `assembleDebug` + `adb install` + manual 2-HP: A invite B, B Terima, cek `adb exec-out run-as cat databases/upme.db`.
- Kill app, `adb shell am force-stop`, buka lagi → tetap login. BEAM `kill -HUP` → client tetap `0x02` ok.

## Risks
- FCM belum setup → tetap poll 5-15s, tidak blokir rilis.
- `EncryptedSharedPreferences` fail di device lama → fallback file tetap butuh `MasterKey`, fallback terakhir log only.

## Open Questions
None — semua bisa dari repo lokal.
