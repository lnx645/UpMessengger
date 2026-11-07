---
name: upchat-build
description: Build UpMessenger hemat RAM 4GB + JWT auth + auto-install ke HP via ADB.
---

# UpChat Build

Skill untuk agent lain agar bisa build UpMessenger secara konsisten di PC 4GB RAM CPU rendah, pakai JWT untuk transport autentikasi, dan langsung install ke device.

## Kapan dipakai
- User minta `build`, `install`, `jwt`, atau `ram 4gb` untuk UpMessenger.
- Agent perlu reproduksi build yang sama tanpa menebak flags.

## JWT Transport Auth (WhatsApp-level)
- Backend: `UpMessenger.Domain.Jwt` RS256 RSA-2048 + `kid` + key rotation (`:upme_jwt_keys`). Legacy HS256 masih diverifikasi untuk migrasi. Generate di `AuthStore.get_or_create_user`, verify stateless. Expiry 30 hari. Secret `UPME_JWT_SECRET` jadi fallback HS256 saja.
- Mobile: `JwtHelper` cek `exp`/`sub`, `AuthPrefs.isLoggedIn` tolak expired. Token di `EncryptedSharedPreferences`.
- 2FA Registration Lock: `Auth` punya `two_fa_hash/salt/enabled` (PBKDF2 100k), `AuthStore.set_two_fa/verify_two_fa`, frame `0x14 verify`, `0x15 set`, `0x16 disable`. `verify_otp` → `0x14 need_two_fa` jika aktif.
- Identity: X25519 `identity_pub` per user, `KeyDirectory.valid_key_package?`, frame `0x20 prekey_publish`.
- Frame auth: `0x02 {token: JWT}` wajib sebelum `0x03 MSG_SEND`. OTP `0x10/0x11` tidak pernah kembalikan OTP asli.

## Build Hemat RAM 4GB
Gunakan flags ini (sudah ada di `mobile/gradle.properties`):
```
org.gradle.jvmargs=-Xmx1536m -XX:+UseParallelGC -XX:MaxMetaspaceSize=512m -Dorg.gradle.vfs.watch=false
org.gradle.workers.max=2
kotlin.daemon.jvmargs=-Xmx1024m
android.useAndroidX=true
```

Perintah build yang benar:
```sh
cd mobile && ./gradlew :app:assembleDebug --offline --max-workers=2
# atau untuk rilis:
cd mobile && ./gradlew :app:assembleRelease --offline --max-workers=2
```

Jangan pakai `--no-daemon` di 4GB (lebih lambat), jangan naikkan `workers.max` >2, jangan naikkan `Xmx` >1536m.

## Auto-Install
Setelah build sukses, langsung install:
```sh
adb devices # pastikan 1 device terbaca
adb install -r mobile/app/build/outputs/apk/debug/app-debug.apk
# atau release:
adb install -r mobile/app/build/outputs/apk/release/app-release.apk
```

Jika `adb` reverse dipakai untuk backend:
```sh
adb reverse tcp:5222 tcp:5222
```

## Verifikasi
- Backend: `cd backend && mix test` harus `0 failures` (termasuk `security_test.exs` untuk JWT).
- Mobile: `BUILD SUCCESSFUL` tanpa error Kotlin.

## Catatan
- Jangan commit `UPME_JWT_SECRET` asli ke repo.
- Untuk prod, set `UPME_JWT_SECRET` minimal 32 byte random dan `UPME_TLS=true` + cert.
