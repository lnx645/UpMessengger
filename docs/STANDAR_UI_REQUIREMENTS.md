# Standar UI Requirements (Production-Grade) + Analisis Senior Mobile Engineer

Tanggal: 2026-11-07 — stack: Kotlin + XML + Material3, target RAM 4GB, font Manrope, gaya WhatsApp.
File ini = requirement tetap. Audit sebelumnya (`AUDIT_UI_REUSABLE.md`) tetap berlaku; file ini menambah sudut pandang senior engineer: apa yang bagus, apa yang melanggar standar industri, dan apa yang wajib dikerjakan per layar.

---

## 1. Penilaian Senior (jujur)

**Yang sudah setara industri:**
- Struktur `presentation/common/` benar: satu tempat untuk `AppCard/AppDivider/AppSheet/AppSwitch/AppToast/ConfirmDialog/SettingItem/SettingSection`. Ini pola design-system yang dipakai Gojek/Shopee/BBM-era.
- Adapter (`ChatAdapter`, `MessageAdapter`) sudah `ListAdapter + DiffUtil + getItemViewType`, tanpa `notifyDataSetChanged`. Pertahankan.
- `contentDescription` di icon penting sudah ada (`Cari, Chat baru, Emoji, Lampiran, Kamera, Kirim, Tambah teman, Tutup`). Bagus — tinggal lengkapi yang kurang.
- `themes.xml` sudah punya bibit benar: `Theme.Material3.DayNight`, `Widget.UpMe.Button.Primary/Outlined/Pill`. Tinggal dipakai konsisten.

**Yang belum setara industri (temuan senior):**
1. **Token bocor.** `themes.xml` + `colors.xml` sudah bagus (brand, bubble, divider + varian dark), tapi layout masih hardcode `#075E54`, `#111B21`, `#667781`, `@android:color/white`, `textSize 11/12/13/14/15/16/18/19sp` acak, `cornerRadius 12/14/18dp` acak. Standar industri: layout TIDAK BOLEH punya nilai mentah — semua via `?attr` / `@style` / `@dimen`.
2. **Dark mode setengah jalan.** `colors.xml` punya `_dark` (`bubble_sent_dark`, `chat_wallpaper_dark`, `divider_dark`…), theme parent `DayNight`, tapi layout pakai `@android:color/white` dan `#111B21` hardcode → dark mode pasti pecah. `fragment_teman.xml`, `fragment_chats.xml`, `item_chat.xml`, `layout_sheet_global.xml` = tersangka utama.
3. **Struktur layout boros.** `fragment_teman.xml` = `FrameLayout` + margin-top absolut (`58dp`, `104dp`) untuk menumpuk search + chip + list. Ini rapuh: pecah di font-scale besar / layar kecil / landscape. Industri: `ConstraintLayout` atau `LinearLayout` vertikal / `CoordinatorLayout + AppBarLayout`, tanpa magic margin.
4. **Duplikat pola.** Search bar Teman vs Chats ditulis ulang manual (icon + EditText + `bg_search`). Empty-state Teman vs Chats ditulis ulang manual. Avatar lingkaran di `item_chat.xml` ditulis manual (FrameLayout 49dp + TextView). Industri: 1 komponen dipakai 2 tempat (`AppSearchBar`, `AppEmptyState`, `AppAvatarView`).
5. **Sheet/dialog 2 standar.** `AppSheet` (benar) vs inflate manual `BottomSheetDialog + layout_sheet_global` di `TemanFragment:236,299,406` + `ProfilFragment:276`. Warna/teks tombol sheet masih hardcode (`#075E54`, `#FFFFFF`, `14sp`) — harusnya pakai style `Widget.UpMe.Button`.
6. **Aksesibilitas belum lolos.** Touch target: avatar 49dp ok, tapi icon search 18dp dan tick/badge 20dp tanpa `minTouchTarget` berisiko <48dp. `item_chat` divider `0.7dp` ok visual tapi jangan jadi satu-satunya pembeda (butuh label — sudah ada, pertahankan). Font-scale: `260dp` fixed + `translationY=40dp` di empty-state + `maxLines=1` di mana-mana = teks kepotong saat font besar.
7. **Perf: overdraw + bayangan.** `item_chat.xml` = `MaterialCardView` per row + nested `LinearLayout` 3 level + divider `View` per row. Untuk list 100+ chat ini mahal. Industri: row = `ConstraintLayout` flat + `MaterialCardView` hanya bila perlu ripple/stroke, divider via `DividerItemDecoration`, bukan View per row. `elevation=1dp` di bubble + input bar menambah shadow pass — ukur dengan GPU overdraw; bila merah, turunkan.
8. **`dimens.xml` kosong.** Tidak ada `@dimen/spacing_*`, `@dimen/radius_*`. Akibatnya `8/10/12/14/16/24/32dp` tersebar acak. Industri: spacing scale 4dp (`4,8,12,16,24,32`), radius scale (`8,12,16,20,28`).

---

## 2. UI Requirements (wajib dipenuhi sebelum production)

### R1. Design token (satu sumber kebenaran)
- [ ] Semua warna layout via `@color/` atau `?attr` — larang `#hex` dan `@android:color/white/black` di layout baru (lint `HardcodedColor` = error).
- [ ] Semua teks via `TextAppearance` (`Widget.UpMe.Text.*`: Title 18 Bold, Body 15/14, Caption 12, Time 11) — larang `textSize` mentah di layout baru.
- [ ] Isi `dimens.xml`: `spacing_4/8/12/16/24/32`, `radius_8/12/16/20/28`, `icon_18/20/24`, `avatar_49`, `row_min_56`, `button_h_50/54`.
- [ ] Tombol hanya 3 style: `Widget.UpMe.Button.Primary / .Outlined / .Primary.Pill` — hapus `backgroundTint` + `cornerRadius` mentah dari semua layout (termasuk `layout_sheet_global.xml:btnSheetPrimary/Secondary`).
- [ ] Font hanya `@font/manrope*` via style — larang `fontFamily` per-TextView (kecuali komponen programmatic yang cache typeface).

### R2. Light/Dark (DayNight beneran)
- [ ] Ganti `@android:color/white` → `?attr/colorSurface`; `#111B21` → `?attr/colorOnSurface`; `#667781` → `?attr/colorOnSurfaceVariant`; divider → `?attr/colorOutlineVariant`.
- [ ] Definisikan di `themes.xml` (light) + `values-night/themes.xml` mapping ke token `_dark` yang sudah ada (`bubble_sent_dark` dll).
- [ ] QA: screenshot tiap layar (Teman, Chats, Detail, Profil, Sheet, Dialog) di light + dark, font-scale 1.0x dan 1.3x.

### R3. Komponen wajib (tambah + migrasi)
- [ ] `AppSearchBar` (icon + clear-button + IME search) — pakai di `fragment_teman` + `fragment_chats`, hapus duplikat manual.
- [ ] `AppEmptyState(icon, title, subtitle, ctaText, onCta)` — pakai di Teman + Chats (+ Updates bila ada). Hapus `translationY=40dp` + `260dp` fixed; pakai center constraint + `maxWidth 280dp`.
- [ ] `AppAvatarView(initial, colorSeed)` — ganti FrameLayout 49dp manual di `item_chat.xml`. Seed warna dari hash PIN (konsisten per teman).
- [ ] `AppSheet.show(Config)` = satu-satunya cara tampilkan sheet. Migrasi `TemanFragment` 3 call-site + `ProfilFragment` QR sheet.
- [ ] `item_chat.xml`: flatten ke `ConstraintLayout`, divider pindah ke `DividerItemDecoration` (inset 73dp dipertahankan via decoration, bukan View per row).

### R4. Layout hygiene (aturan keras)
- [ ] Larang margin-top absolut untuk menumpuk section (`58dp/104dp` di `fragment_teman`) — ganti `LinearLayout` vertikal atau `ConstraintLayout` chain.
- [ ] Maksimal 8 level view per layar; row list maksimal 3 level.
- [ ] `RecyclerView`: `setHasFixedSize(true)` (sebagian sudah), `itemViewCacheSize` wajar, `RecycledViewPool` bersama untuk daftar sejenis, `stableIds` bila ada animasi.
- [ ] Gambar via Coil + thumbnail + placeholder; `ImageView` list wajib `fixed size` agar tidak re-layout.
- [ ] `ViewStub` untuk sheet konten berat / dialog QR / cropping (sudah lazy sebagian — pertahankan).

### R5. States (tiap layar list wajib 4 state)
- [ ] Loading: skeleton row (bukan spinner tengah) untuk Chats/Teman ≤800ms pertama.
- [ ] Empty: `AppEmptyState` + CTA yang beraksi (Invite Teman → buka sheet invite, bukan toast).
- [ ] Error + Offline: ilustrasi + tombol Coba lagi; bedakan "tidak ada data" vs "gagal muat".
- [ ] Tombol: Enabled/Loading (disable + alpha 0.6, teks tetap — JANGAN kosongkan teks)/Disabled. Unifikasi `setLoading` tunggal (lihat AUDIT_UI U4).

### R6. Aksesibilitas (blocking untuk rilis enterprise)
- [ ] Semua kontrol klik ≥48dp area sentuh (`android:minWidth/minHeight` atau `padding` pembesar).
- [ ] Semua icon-only wajib `contentDescription` (ID + EN bila perlu). Audit: search icon di `fragment_teman` belum ada desc — tambah.
- [ ] Kontras ≥4.5:1 untuk teks; cek `#667781` di atas `search_bg #F0F2F5` (bukan hanya di atas putih).
- [ ] TalkBack order logis: nama → preview → waktu → badge; test dengan TalkBack on.
- [ ] Tidak ada info yang hanya warna (mis. tick biru) — harus ada label teks pendamping (sudah ada "PIN: …", pertahankan).

### R7. Motion & feedback
- [ ] Durasi standar: fade 150ms, sheet slide 250ms (`@android:interpolator/fast_out_slow_in`), ripple di semua row.
- [ ] Debounce tunggal per-view (`elapsedRealtime`, 500ms tombol / 800ms navigasi). Hapus `ClickGuard` global + `currentTimeMillis` (lihat AUDIT_UI U3).
- [ ] Haptic ringan (`HapticFeedbackConstants.KEYBOARD_TAP`) untuk Terima/Tolak/Kirim.

### R8. Quality gate
- [ ] `lint` (`HardcodedText` untuk user-visible → pindah `strings.xml` ID/EN; `HardcodedColor`; `ContentDescription`; `Overdraw`; `TooDeepLayout`) = 0 error pada `release`.
- [ ] Screenshot test untuk `AppEmptyState`, `item_chat` (light/dark), `AppSheet` — cegah regresi visual.
- [ ] Manual matrix: 5" + 6.5" + tablet 10", portrait + landscape, font 1.0x/1.3x, light/dark, offline/online.

---

## 3. Backlog per layar (urut kerja senior)

1. **Token dulu** (`themes/dimens/strings`): 1 PR, tanpa ubah tampilan — hanya ganti mentah → token. Risiko rendah, buka jalan dark mode.
2. **Row chat** (`item_chat.xml` + `ChatAdapter`): flatten + `DividerItemDecoration` + `AppAvatarView`. Ukur scroll 60fps sebelum/sesudah (Profile GPU Rendering).
3. **Teman + Chats**: `AppSearchBar` + `AppEmptyState` + hapus magic margin + tambah state Error/Offline + desc search.
4. **Sheet global**: migrasi 4 call-site ke `AppSheet` + style tombol via `Widget.UpMe`.
5. **Detail chat**: input bar (`fragment_detail.xml`) rapikan icon touch-target 48dp, bubble hapus `elevation` bila overdraw merah, pastikan `maxLines=5` + auto-scroll benar.
6. **Dark + font-scale + TalkBack pass** untuk semua layar di atas.

*Prinsip senior: tampilannya boleh meniru WA, tapi fondasinya harus token + komponen — bukan copy-paste layout. Kerjakan token → komponen → layar, jangan sebaliknya.*
