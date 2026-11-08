# TODO Teman 100% — Sisa Integrasi Server & Polish

> Status saat ini 78% (audit 2026-11-07). File ini backlog 100% — belum ada kode diubah.

## Definisi Done
Teman bisa invite via PIN/QR/Kontak antar 2 HP real, pending sync server, accept/reject sinkron, search & hapus/blokir konsisten, avatar & empty-state reusable.

## Checklist

### A. Server integration (arus utama pending)
- [ ] A1 `0x34 pending_list` wire → `syncPending()` simpan ke `pendingDao` + update badge `chipPending` count
- [ ] A2 `0x35 accept` / `0x36 reject` 2-HP E2E (ETS `:upme_pending` + client `temanDao`/`pendingDao` sinkron balik, toast & list auto-refresh)
- [ ] A3 `0x38 search_users` → `inputSearch` debounce 300ms call server, merge hasil local+remote (max 20)
- [ ] A4 `0x39 phone_for_pin` → `Kontak` `READ_CONTACTS` permission + `phone.normalize` + `requestOnce(0x39)` + autofill `inputPinInvite` + auto-invite
- [ ] A5 `0x32 whoCanAdd/blockInvite` gate di UI (jika `blocked` → toast `Tidak bisa invite, user memblokir invite`)
- [ ] A6 FCM `0x3A register_fcm` + `MyFirebaseService` + `NotificationChannel teman` + BE `FcmPush` on `0x32` (prasyarat `google-services.json` baru `com.upme.messenger` Opsi A, `priv/fcm.json` chmod 600)

### B. UX & reusabilitas
- [ ] B1 Avatar pending/teman load file `avatar.jpg` (ganti inisial saja) + Coil `size(48)` + placeholder
- [ ] B2 `AppEmptyState` pakai di `fragment_teman` & `fragment_chats` (ganti LinearLayout empty manual)
- [ ] B3 `AppSearchBar` pakai di `fragment_teman` (ganti EditText manual bg_search)
- [ ] B4 QR `CAMERA` runtime permission `requestPermissionLauncher` + fallback `Toast` jika denied
- [ ] B5 Pending → teman animasi `ListAdapter DiffUtil` + `submitList` tanpa `rv.adapter=` swap tiap poll

### C. Keandalan
- [ ] C1 Bedakan error `network` vs `401 unauth` di `TemanFragment` (jangan anggap logout untuk IOException)
- [ ] C2 Poll 5s → FCM push (defer) + keep poll 15s fallback bila FCM tidak ada
- [ ] C3 E2E test 2 device: HP A `+62...003` invite HP B, B lihat `Permintaan`, Terima, A lihat `Teman` tanpa restart

## Urutan eksekusi nanti
1. A1 → A2 → A4 → A3 → A5 → B1 → B4 → B2/B3 → C1
