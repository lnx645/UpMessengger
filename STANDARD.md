# UpMessenger Standard - Mesin 4GB & Low Resource (Production)

> Aturan wajib sebelum tulis kode. Target: RAM rendah, CPU rendah, startup < 800ms, scroll 60fps.

## 1. Mobile (Android Kotlin + XML, No Compose, Material3)
- 100% XML di `mobile/app/src/main/res/layout`, navigasi `Fragment + NavGraph XML`.
- Dilarang: Jetpack Compose, `notifyDataSetChanged`, load history penuh.
- List wajib: `RecyclerView + ListAdapter + DiffUtil + getItemViewType + Paging3 LIMIT 50`.
- DB: `Room + SQLCipher`, index `(conversation_id, timestamp)`.
- Gambar: `Coil`, thumbnail dulu, `ViewStub` untuk layout jarang dipakai.
- Startup: `Application.onCreate` minimal, init berat lazy via `androidx.startup.Initializer`.
- DI manual (tanpa Hilt/KSP) agar build hemat RAM 4GB.
- Release: `minifyEnabled + shrinkResources`, vector drawable.

## 2. Gradle Hemat RAM 4GB
- Lihat `mobile/gradle.properties`, Xmx maks 1536m, workers.max=2.
- Build: `./gradlew :app:assembleDebug --offline --max-workers=2`
- Dev pakai HP fisik via adb, jangan emulator.

## 3. Backend Elixir
- Clean Arch: `domain / application / infrastructure / interface`.
- Tiap modul `@moduledoc`, tiap fungsi `@doc + @spec` + komen inline.
- TCP: `Thousand Island`, framing length+type+payload, heartbeat 30s, resume offset.
- Server hanya relay ciphertext, tidak decrypt.
