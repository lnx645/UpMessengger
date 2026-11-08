# Re-Audit UI + Teman (perubahan terbaru, dicek 19:20+)

Metode: file mtime > 18:00 (tanpa git — `.git/` kosong, tidak bisa diff; disarankan `git init` + commit agar audit berikutnya bisa diff beneran).
Yang berubah: `Dimens.kt` (baru), `AppAvatarView`/`AppSearchBar`/`AppEmptyState` (baru), `WaButtonExt`, `DebouncedClick`, `SettingItem`/`AppSwitch` (FontCache), `PinValidator`+Test (baru), `HostUtil` (baru), `TemanFragment`, `ProfilFragment`, `TcpClient`, `themes.xml`, `attrs.xml`, `fragment_profil.xml`.

## 1. Yang BENAR-BENAR membaik ✅ (diakui senior)
- **U5 selesai**: `private fun dp` di 4 file sudah hilang (`rg` kosong). Tinggal 1 sumber `Dimens.kt`.
- **U3/U4 setengah selesai**: `WaButtonExt.debounceClick` per-view via tag + `elapsedRealtime` ✅; `setLoading(isLoading, progress, loadingText)` terunifikasi ✅; `ClickGuard` global di-deprecate ✅; `DebouncedClick` jadi no-op deprecated ✅.
- **U7 selesai**: `FontCache` di `SettingItem` + `AppSwitch` — tidak lagi `getFont` tiap instance ✅.
- **PIN terpusat**: `PinValidator` (isValid/normalize/pretty) + dipakai di `TemanFragment` + `ProfilFragment` + ada `PinValidatorTest` ✅. Pola yang benar.
- **Sheet mulai migrasi**: `TemanFragment:238,401` sudah pakai `AppSheet.show(Config)` ✅.
- **`attrs.xml` lahir**: `AppDivider/insetStart` jadi styleable resmi ✅ (hapus fallback raw-attr di kode bila sudah migrasi penuh).

## 2. Yang BARU tapi BELUM SIAP PAKAI ⚠️ (jangan diklaim selesai)
1. **Komponen baru belum dipakai di mana pun.** `AppSearchBar`, `AppEmptyState`, `AppAvatarView` hanya ada sebagai file — `fragment_teman.xml` masih search manual + empty manual + magic margin `58/104dp`; `item_chat.xml` masih avatar manual. Komponen yang tidak dipakai = dead code baru (U8 belum selesai, baru 30%).
2. **`Dimens.kt` salah sumber metrik.** Pakai `Resources.getSystem()` — tidak ikut konfigurasi activity (font-scale, density override, multi-window). Senior fix: `context.resources.displayMetrics` / `TypedValue.applyDimension`. Plus API ganda (`Int.dp`, `Float.dp`, `dp()`, `dpF()`, `dpToPx`) — pilih 1 kanonis (`Int.dp(ctx)` atau ext `Resources`), hapus sisanya.
3. **`AppAvatarView` belum production.** Hardcode ungu `0xFF9C27B0` + `bg_avatar_parrot` + `textSize 28f` fixed, tanpa attrs (size/initial/bgSeed), tanpa seed warna dari hash PIN (yang dijanjikan di requirement). Juga load font di `init` tanpa cache.
4. **`AppSearchBar` belum setara search bar industri.** Tanpa icon search, tanpa tombol clear (X), tanpa `imeOptions=actionSearch`, hint hardcode `"Cari teman..."`, tanpa attrs. EditText programmatic menyulitkan styling/a11y — pertimbangkan inflate layout atau minimal expose `imeOptions + onSearchAction + showClear`.
5. **`AppEmptyState` melanggar token.** Hardcode `0xFF111B21/0xFF667781`, `MaterialButton` programmatic tanpa style `Widget.UpMe` (kehilangan theme, dark mode pecah), tanpa `contentDescription` icon, tanpa maxWidth. Wajib pakai `?attr` + style + desc.
6. **`ClickGuard.canClick()` tanpa view masih dipakai 9 titik** (`PinFragment:38,46,57`, `TemanFragment:301,306,312`, `ChatDetail:81`, `PhoneFragment:24,49`) — dead-deprecation. Migrasi ke `view.debounceClick {}` agar fix U3 tuntas.
7. **`PinValidator.generate()` pakai `HEX.random()`** (Kotlin Random, predictable) untuk generate PIN — harus `SecureRandom`. Validasi ok, generator belum.
8. **`HostUtil.resolveHosts()` hardcode IP dev** (`192.168.70.92/.93/.127`) di path non-dev — bocor ke prod bila primary bukan 127. Pisahkan `debug/` vs `release/` (BuildConfig), jangan fallback IP privat di release.
9. **`TcpClient` masih tanpa `0x38/0x39`** — gap pertemanan (search server, phone lookup) BELUM tersentuh. `fragment_teman.xml` juga belum berubah (masih `@android:color/white`, magic margin). `values-night/` masih TIDAK ADA. Manifest masih tanpa `allowBackup=false`.

## 3. Checklist lanjutan (urut senior)
- [ ] Wiring: pakai `AppSearchBar`+`AppEmptyState` di `fragment_teman` (+ chats), `AppAvatarView` di `item_chat`; hapus layout manual lama.
- [ ] Betulkan `Dimens.kt` (context-based, 1 API) + `AppAvatarView` (attrs + hash-seed + FontCache) + `AppSearchBar` (icon/clear/IME/attrs) + `AppEmptyState` (token + style + desc).
- [ ] Migrasi 9 `ClickGuard.canClick()` → `debounceClick`; hapus `ClickGuard` global sepenuhnya.
- [ ] `PinValidator.generate` → `SecureRandom`; `HostUtil` → split debug/release.
- [ ] Lanjut ke yang belum tersentuh: `0x38/0x39` + sync pending + `values-night` + backup flags + SQLCipher (lihat `AUDIT_TEMAN_DAN_PRODUCTION.md` S1–S10).

Kesimpulan: arah benar dan 4 fix (dp, font cache, debounce, PIN) layak diapresiasi — tapi klaim "reusable selesai" belum boleh. Komponen baru = 30% (dibuat, belum dipakai, belum token-compliant).
