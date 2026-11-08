# Catatan: Mnesia untuk Distribusi & Replikasi (Scaling)

**Status:** Ditunda — tetap `ETS+DETS` untuk sekarang, cocok untuk single HP/server (RAM 4GB).
**Alasan:** `ETS+DETS` paling hemat RAM & simpel. Belum butuh multi-node.

## Kapan pakai Mnesia
- Saat butuh 2+ node (HP/server kedua) yang saling replikasi otomatis.
- Ingin `disc_copies` tetap low-RAM tapi auto-sync `upme_users`, `jwt_keys`, `revoked`.
- Alternatif: Redis/`Horde`/`DeltaCrdt` — tapi Mnesia native Erlang, tanpa deps ekstra.

## Trade-off
- `ETS+DETS` : simpel, file-based, restart = `load_users!` dari `priv/*.dets`, tidak auto-replicate.
- `Mnesia` : `ram_copies` + `disc_copies`, schema `mnesia:create_schema`, perlu handle `net_split` (split-brain), backup `mnesia:backup`.

## Rencana migrasi (jika scale)
1. `Queue` tetap `ETS` (ephemeral).
2. Tabel `upme_users`, `jwt_keys`, `revoked` → `mnesia:create_table(..., [{disc_copies, [node()]}])`.
3. API mirip `ets` → ganti `:ets.insert/:ets.lookup` dengan `:mnesia.dirty_write/:mnesia.dirty_read` atau transaksi `mnesia:transaction`.
4. Tambah `mnesia:change_config(extra_db_nodes, [node2])` saat node baru join.

## Keputusan
**Jangan dulu.** Lanjut fitur prioritas (avatar cropping, Sheet, E2EE). Catatan ini jadi patokan saat horizontal scaling dibutuhkan.

Tanggal: 2026-11-07
