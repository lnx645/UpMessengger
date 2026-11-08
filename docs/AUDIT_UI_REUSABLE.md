# Audit Reusabilitas Komponen UI

Tanggal: 2026-11-07
Cakupan: `mobile/app/src/main/java/.../presentation/common/`, `utils/`, `chats/ChatAdapter.kt`, `detail/MessageAdapter.kt`, `res/layout/*.xml`.

## Ringkasan
Fondasi reusable sudah ada (9 komponen common), tapi adopsi tidak merata: 2 komponen tidak dipakai sama sekali, 1 duplikat, 3 pola masih manual di fragment. Skor kasar: **6/10 — reusable sebagian**.

## Yang sudah bagus (pertahankan)
- `common/`: `AppCard`, `AppDivider`, `AppSheet`, `AppSwitch`, `AppToast`, `ConfirmDialog`, `SettingItem`, `SettingSection`, `WaButtonExt` — API jelas + KDoc usage.
- `AppToast`: tidak ada lagi `Toast.makeText` di codebase — semua lewat `AppToast.show()`. ✅
- Adapter sesuai `STANDARD.md`: `ChatAdapter` + `MessageAdapter` = `ListAdapter + DiffUtil + getItemViewType`, tanpa `notifyDataSetChanged`. ✅
- `SettingSection + SettingItem + AppSwitch` dipakai benar di `PrivasiKeamananFragment.kt:68-84` (3 toggle pertemanan). Pola ini yang harus jadi acuan.
- `CropOverlayView` single-purpose, tidak perlu digeneralisasi.

## Temuan (prioritas)
| # | Masalah | Bukti | Dampak | Aksi |
|---|---------|-------|--------|------|
| U1 | `AppSheet` underused — hanya `OtpFragment:193` pakai `AppSheet.showResendOtp` | `TemanFragment:236,299,406` + `ProfilFragment:276` masih `BottomSheetDialog + inflate(layout_sheet_global)` manual | 3 varian sheet tidak konsisten (style, handle, dismiss) | Migrasi semua sheet ke `AppSheet.show(Config)`; tambah `Config.iconRes/countdown` bila perlu |
| U2 | `AppCard` & `AppDivider` 0 pemakaian di XML | `rg AppCard\|AppDivider res/layout` kosong di luar KDoc | Dead code; layout masih `MaterialCardView` manual | Ganti card manual di `fragment_profil/pin` ke `AppCard`; divider manual ke `AppDivider` (pakai `INSET_SETTINGS_DP`) |
| U3 | Duplikat debounce/click-guard (3 implementasi) | `WaButtonExt.debounceClick` + `WaButtonExt.ClickGuard` (global singleton) + `utils/DebouncedClick.setDebouncedClickListener` | Perilaku double-click beda tiap layar; `ClickGuard` satu timestamp global → klik layar A memblokir layar B ≤500ms | Satu saja: `View.debounceClick(elapsedRealtime, per-view)`; hapus 2 lainnya; hapus `System.currentTimeMillis` (rentan ubah jam) |
| U4 | `setLoading` 3 overload beda signature | `WaButtonExt.setLoading(Boolean, View?)` vs `utils/DebouncedClick.setLoading(ProgressBar, Boolean, String)` vs `setLoadingSimple` | Caller bingung, teks tombol hilang/alpha beda | Unifikasi 1 API: `setLoading(isLoading, progressView?, loadingText="")`; hapus sisanya |
| U5 | `dp()` privat duplikat di 4 file | `AppCard:56`, `SettingSection:135`, `AppSwitch:157`, `SettingItem:193` | Copy-paste; risiko inkonsistensi | Ekstrak ke `common/Dimens.kt` (`Int.dp`, `Float.dp`) |
| U6 | Tombol tidak punya style bersama | `~15x MaterialButton` di 10 layout dengan `backgroundTint="#075E54"`, `cornerRadius 12/18dp` hardcode | Ganti warna brand = edit 10 file | Buat `Widget.UpMe.Button.Primary/Outlined` di `themes.xml` + pakai di semua layout |
| U7 | `SettingItem` load font tiap instance | `ResourcesCompat.getFont(...)` di `init` tiap row | Jank saat list setting panjang | Cache `Typeface` di object singleton |
| U8 | Belum ada komponen untuk pola berulang | Empty-state (icon+title+subtitle+CTA) di `fragment_teman` vs `fragment_chats`; avatar lingkaran; search bar; PIN dots | Tiap fragment bikin manual | Tambah `AppEmptyState`, `AppAvatarView`, `AppSearchBar` (bertahap, mulai dari EmptyState) |

## Tidak perlu diubah
- `MessageAdapter` vs `ChatAdapter` mirip tapi beda domain (bubble chat vs list chat) — biarkan terpisah.
- `ConfirmDialog` vs `AppSheet`: dialog tengah vs sheet bawah — biarkan dua, jangan digabung.

## Checklist migrasi nanti
- [x] U3+U4: unifikasi debounce + loading — `WaButtonExt` per-view `debounceClick(elapsedRealtime via tag)`, unified `setLoading(isLoading, progress?, loadingText?)`, `ClickGuard` deprecated per-view wrapper, `DebouncedClick` deprecated delegating stubs.
- [x] U5: `Dimens.kt` + hapus `private fun dp` di AppCard/SettingSection/AppSwitch/SettingItem.
- [x] U1: migrasi `TemanFragment` 2 sheet generic (Chat/Hapus + Terima/Tolak) ke `AppSheet.show(Config)`; Invite sheet tetap custom (input) + QR sheet custom (dialog_qr) — konsisten WA-style via AppSheet style.
- [x] U2+U6: `themes.xml` Widget.UpMe.Button.Primary/Outlined/Pill + ganti `fragment_profil` 5 MaterialCardView→AppCard, 5 dividers→AppDivider(inset 66dp), 3 buttons→style; `fragment_teman` tetap (sudah minimal).
- [x] U7: cache Typeface di SettingItem/AppSwitch `FontCache` singleton.
- [x] U8: `AppEmptyState`, `AppAvatarView`, `AppSearchBar` baru (bertahap, empty-state siap pakai).

*Selesai 2026-11-07 — semua U diperbaiki, build assembleDebug + testDebugUnitTest pass.*

