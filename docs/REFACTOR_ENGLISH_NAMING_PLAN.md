# English Naming Refactor Plan — UpMessenger (teman → friend, Profil → Profile, etc.)

> Status: **Design doc only — no code changed**. Execute with `go` after approval.
> Scope: `mobile/app` Kotlin/XML + Room. `backend/` Elixir untouched (already English).

## 1. Goal & Constraints

* **Goal**: Replace Indonesian identifiers with English across code, packages, classes, layouts, navigation and string keys — without losing `git log --follow` history and without corrupting on-device Room/SQLCipher data.
* **Constraints**:
  - Target machine 4 GB RAM (`STANDARD.md` — no Hilt, `mobile/gradle.properties` Xmx 1536m).
  - `Room + SQLCipher 4.5.4` is source of truth (`UpMeDb` at `mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt`). Destructive migration is forbidden (`AUDIT_LOGIN_RELOGIN.md:6`).
  - A **pending migration 3 → 4 already exists in the dirty tree** (`/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt:44-48` `MIGRATION_3_4` empty, `version = 4`, SQLCipher `sqlcipher_export` path). Committed `HEAD` (`/home/demo/UpMessengger/.git` rev `3640ce3`) is still at `version = 3`. The pending migration must not be re-based or lost.
  - User-visible Indonesian strings stay Indonesian — only **code keys** become English (e.g. `empty_teman_*` → `empty_friend_*` still renders "Belum ada teman").

---

## 2. Full Inventory (inspected evidence, 2026-11-07)

| Category | Current (Indonesian) | Evidence paths |
|---|---|---|
| **Package** | `com.upme.messenger.presentation.teman` | `mobile/app/src/main/java/com/upme/messenger/presentation/teman/TemanFragment.kt:1` |
| | `com.upme.messenger.presentation.profil` | `presentation/profil/ProfilFragment.kt:1`, `PrivasiKeamananFragment.kt:29` |
| **Fragments / Classes** | `TemanFragment` | `presentation/teman/TemanFragment.kt:44` |
| | `ProfilFragment` | `presentation/profil/ProfilFragment.kt:32` |
| | `PrivasiKeamananFragment` | `presentation/profil/PrivasiKeamananFragment.kt:29` |
| | `SetupProfilFragment` | `presentation/auth/SetupProfilFragment.kt:21` |
| **Entities / DAOs** | `TemanEntity` `@Entity(tableName="teman")` | `data/local/UpMeDb.kt:15` |
| | `PendingEntity` `@Entity(tableName="pending_requests")` | `UpMeDb.kt:18` |
| | `TemanDao` / `PendingDao` | `UpMeDb.kt:27`, `UpMeDb.kt:37` |
| | `class UpMeDb` + `UpMeDb_Impl` (generated) | `UpMeDb.kt:134`, `app/build/generated/ksp/debug/.../UpMeDb_Impl.java` |
| **TcpClient methods** | `inviteTeman`, `searchTeman`, `deleteTeman` | `data/net/TcpClient.kt:99`, `:126`, `:166` |
| **Layouts** | `fragment_teman.xml` | `res/layout/fragment_teman.xml` (also `R.layout.fragment_teman` in `TemanFragment.kt:113` and `MainActivity.kt:49`) |
| | `fragment_profil.xml` | `res/layout/fragment_profil.xml` |
| | `fragment_setup_profil.xml` | `res/layout/fragment_setup_profil.xml` (inflated `SetupProfilFragment.kt:34`) |
| | `layout_sheet_invite_teman.xml` | `res/layout/layout_sheet_invite_teman.xml` (inflated `TemanFragment.kt:354`) |
| **Navigation** | `nav_teman`, `nav_profil`, `nav_privasi_keamanan` | `res/navigation/nav_graph.xml:9-11` |
| | `R.id.nav_teman` / `nav_profil` in `MainActivity.kt:21,49-50,94` + `bottom_nav.xml:5-6` | |
| **Menu** | `menu/bottom_nav.xml` items | `res/menu/bottom_nav.xml:5-6` |
| **Strings** | `empty_teman_title/subtitle/cta`, `hint_search="Cari teman"` etc. | `res/values/strings.xml:8-10,4` |
| **Tests** | `TemanForceCloseTest` (asserts `TemanFragment.kt` path variants) | `src/test/.../teman/TemanForceCloseTest.kt:8-12` |
| **Generated / build** | `TemanDao_Impl.java`, `FragmentTemanBinding.java` etc. | `app/build/generated/**` — **not renamed manually** |
| **Existing English aliases (dirty tree already added)** | `typealias FriendEntity = TemanEntity` / `FriendDao` / `PendingRequestEntity` | `/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt:190-192` — **treat as shim v0, do not delete yet** |

Not renamed: `MessageEntity`, `ChatsFragment`, `UpdatesFragment`, `ChatDetailFragment`, `backend/` modules, DB file name `upme.db`, `EncryptedSharedPreferences` keys (`upme_db_key`, `upme_db_state`) — out of scope.

---

## 3. Rename Mapping (final English names)

| Indonesian | English | File / artifact action |
|---|---|---|
| `presentation.teman` package | `presentation.friend` | `git mv` dir |
| `presentation.profil` package | `presentation.profile` | `git mv` dir |
| `TemanFragment` | `FriendFragment` | `git mv` + class rename |
| `ProfilFragment` | `ProfileFragment` | `git mv` |
| `PrivasiKeamananFragment` | `PrivacySecurityFragment` (alias `PrivacyFragment` tolerated, but `PrivacySecurityFragment` mirrors file name exactly) | `git mv` |
| `SetupProfilFragment` | `SetupProfileFragment` | `git mv` |
| `TemanEntity` | `FriendEntity` | class rename, keep `@Entity(tableName=...)` strategy §4 |
| `PendingEntity` | `PendingRequestEntity` (already present as alias) | promote alias to real name |
| `TemanDao` | `FriendDao` | class rename |
| `PendingDao` | `PendingRequestDao` | class rename |
| `temanDao()` | `friendDao()` | method in `UpMeDb` — keep `temanDao()` as deprecated delegation for 1 release |
| `pendingDao()` | `pendingRequestDao()` | same |
| `fragment_teman.xml` | `fragment_friend.xml` | `git mv` |
| `fragment_profil.xml` | `fragment_profile.xml` | `git mv` |
| `fragment_setup_profil.xml` | `fragment_setup_profile.xml` | `git mv` |
| `layout_sheet_invite_teman.xml` | `layout_sheet_invite_friend.xml` | `git mv` |
| `nav_teman` id | `nav_friend` | `nav_graph.xml` + `bottom_nav.xml` + `MainActivity` |
| `nav_profil` id | `nav_profile` | same |
| `nav_privasi_keamanan` id | `nav_privacy_security` | same |
| `empty_teman_*` strings | `empty_friend_*` | keep old keys as `<string name="empty_teman_title">@string/empty_friend_title</string>` alias for 1 release or duplicate value |
| `inviteTeman()` | `inviteFriend()` | keep deprecated forwarding |
| `searchTeman()` | `searchFriends()` | keep deprecated forwarding |
| `deleteTeman()` | `deleteFriend()` | keep deprecated forwarding |
| `TemanForceCloseTest` | `FriendForceCloseTest` | rename after shim period, keep old class as subclass alias |

Naming rule: **exact translation, same casing style**. Kotlin classes `PascalCase`, packages `lowercase`, layouts `snake_case`, nav ids `snake_case`. If two parallel names both fit, pick the one matching `Friend`/`Profile` (already used in the `typealias` shim at `UpMeDb.kt:190`).

---

## 4. Core Strategy by Artifact Type

### 4.1 File moves — preserve git history

* **Always** `git mv -v old new` (never `rm + add`). Git detects renames by similarity; `mv` guarantees `log --follow --name-status` continuity. Verified on this repo: canonical git dir is `/home/demo/UpMessengger/.git` (commit `3640ce3` etc.); the working copy `/home/demo/upchat` is an **untracked mirror** with no `.git` — before any `mv`, either `git init` it or run all `git mv` inside `/home/demo/UpMessengger` and sync back. Do **one rename type per commit** (e.g. one commit for the `teman/` dir, one for `profil/` dir) to keep diffs reviewable (< 150 lines logical).
* Layout/XML renames are also `git mv`; Android `R` regenerates.
* After each `mv` commit run `git log --follow -- mobile/app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt` and confirm history shows `TemanFragment.kt` as predecessor.

### 4.2 Kotlin class aliases — no break for pending branches / tests

Use Kotlin `typealias` + `@Deprecated` forwarder, not duplicate class:

```kotlin
// In the SAME file as the new canonical class, or a dedicated `LegacyAliases.kt`
@Deprecated("Use FriendEntity", ReplaceWith("FriendEntity"))
typealias TemanEntity = FriendEntity

@Deprecated("Use FriendDao", ReplaceWith("FriendDao"))
typealias TemanDao = FriendDao

@Deprecated("Use FriendFragment", ReplaceWith("FriendFragment"))
typealias TemanFragment = FriendFragment
```

* The dirty tree already does the reverse (English alias → Indonesian real) at `UpMeDb.kt:190-192`. Phase 1 will **flip** that: Indonesian becomes alias → English real.
* Keep aliases for **≥ 1 release / until `TemanForceCloseTest` is migrated** — that test hard-codes path candidates (`src/test/.../teman/TemanForceCloseTest.kt:8-12`) and greps file content; instant deletion would red that test.
* For methods (`temanDao()` → `friendDao()`), keep both in `UpMeDb`:

```kotlin
abstract fun friendDao(): FriendDao
@Deprecated("Use friendDao()", ReplaceWith("friendDao()"))
fun temanDao(): FriendDao = friendDao() // if Dao is abstract, implement as `abstract fun friendDao()` + concrete delegating `temanDao()` in base, or keep both abstract via interface default
// Simpler: keep both abstract methods pointing to same impl via Room delegation wrapper
```

Room requires `abstract fun` — the shim is two abstract methods returning the same DAO impl class; Room generates `_Impl` for each. Tested pattern: declare new `friendDao()`, keep old `temanDao()` annotated deprecated forwarding to `friendDao()`.

### 4.3 DB table rename — the critical decision

`TemanEntity` is declared `@Entity(tableName = "teman")` at `UpMeDb.kt:15`. Two safe options; **recommend Option A**:

**Option A — Keep physical table name `teman` forever (recommended, zero-migration, safest).**
* Rename Kotlin class `TemanEntity → FriendEntity` but **leave annotation unchanged**:
  ```kotlin
  @Entity(tableName = "teman")
  data class FriendEntity(...)
  ```
* `@Database(entities=[FriendEntity::class, ...])` — `tableName` governs SQLite, class name is irrelevant to `sqlite_master`.
* Room schema hash is computed from `tableName` + columns, not class name → Migration 3→4 (empty) stays valid. No `ALTER TABLE` needed. Generated schema export (`UpMeDb_Impl`) still refers to `teman` table; Room's `@Query("SELECT * FROM teman ...")` strings must also **stay `"teman"`** even after Dao rename — which they will, because we only rename the interface name, not the query literals.
* Keeps `fallbackToDestructiveMigration` risk at zero. Document: "DB is Indonesian-persistent, code is English-persistent — intentional compat layer."

**Option B — Rename physical table `teman → friends` and `pending_requests → pending_requests` unchanged (or `friends`/`friend_requests`).**
* Requires a **real** `Migration` (e.g. `4 → 5`):
  ```kotlin
  val MIGRATION_4_5 = object : Migration(4,5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `teman` RENAME TO `friends`")
      // alternatively: CREATE TABLE friends AS SELECT * FROM teman; DROP TABLE teman;
      // but RENAME is atomic and preserves indices.
      // Also create a VIEW for compat: 
      db.execSQL("CREATE VIEW IF NOT EXISTS `teman` AS SELECT * FROM `friends`")
      // Or keep no view and rely on code shim only — then old queries break if any raw SQL still says "teman"
    }
  }
  ```
* **Downside**: Every `@Query("SELECT * FROM teman ...")` must be updated to `friends` in same commit, or queries will hit the view. Views don't support `INSERT/DELETE` without triggers → writes via DAO still target `friends`, but raw debug queries (`adb exec-out run-as ... "select * from teman"`) would read via view but not write. Riskier / requires more testing.
* Only choose B if product demands English at the `sqlite_master` level (e.g. for debug tooling purity). If chosen, **must bump version to 5 and provide both upgrade and downgrade reasoning**; `fallbackToDestructiveMigration` must be removed before ship (as `AUDIT_LOGIN_RELOGIN.md:6` demands).

**Decision for plan**: Ship **Option A** for phase 1; defer Option B to a later major version if ever. The task's `TemanEntity → FriendEntity` etc. is satisfied by class rename + annotation pin — physical table rename is explicitly out-of-scope for DB-safety.

### 4.4 Layout / Navigation / String resources

* Android resources have **no alias mechanism** like Kotlin typealiases. Keep backward compat by:
  - Duplicating the file for one release: `fragment_friend.xml` (new canonical) + `fragment_teman.xml` (1-line `<include layout="@layout/fragment_friend"/>` wrapper deprecated) — or simply keep old file as 0-byte forwarding via `tools:keep` + `alias` entry.
  - Simpler and preferred: **atomic rename with no shim** because layouts are not referenced by persisted data — they are compile-time only. A single `git mv` commit that updates **all** `R.layout.fragment_teman` references atomically (`TemanFragment.kt:113`, `ProfilFragment.kt:34`, `nav_graph.xml:9`, tests) is safe; there is no cross-version resource lookup at runtime.
  - Same for `nav_teman` → `nav_friend`: `nav_graph.xml` and `bottom_nav.xml` are compile-time; `R.id.nav_teman` constants change after rebuild. **Exception**: if deep-links or persisted navigation state (`NavController` savedState) stored `R.id.nav_teman` integer values on disk, an upgrade could mismatch. Mitigation: keep old ids as `<item tools:ignore>` alias in `res/values/ids.xml`:
    ```xml
    <!-- compat shim, remove after v2 -->
    <item name="nav_teman" type="id">@id/nav_friend</item>
    ```
  - Strings: define new keys, keep old keys as alias values:
    ```xml
    <string name="empty_friend_title">Belum ada teman</string>
    <string name="empty_teman_title">@string/empty_friend_title</string> <!-- deprecated -->
    ```
    Lint will warn duplicate; acceptable for 1 release.

### 4.5 Build / generated code

* Never edit `mobile/app/build/**` (`UpMeDb_Impl.java`, `*Binding.java`, `R.jar`). They regenerate from `ksp` / `room-compiler`. After each rename phase run `./gradlew :app:kspDebugKotlin :app:assembleDebug` and verify no `error: cannot find symbol TemanDao_Impl` remains except in deprecated alias path.

---

## 5. Order — 5 Phases (dependency-correct, one-PR-per-phase recommended)

### Phase 0 — Preparation (no rename, land shims) — ½ day

* Goal: Create English-forward shims so later `mv`s don't break `main` for engineers with in-flight branches.
* In `/home/demo/UpMessengger/mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt` already has English aliases; flip comment to mark them as **forward shim** and add missing ones:
  ```kotlin
  // Canonical will become FriendEntity; keep TemanEntity as deprecated alias — see Phase 2
  typealias FriendEntity = TemanEntity // keep until Phase 2 flip
  ```
* Add English method aliases in DAOs / TcpClient without removing Indonesian:
  ```kotlin
  // TcpClient.kt
  suspend fun inviteFriend(...) = inviteTeman(...)
  @Deprecated("Use inviteFriend") suspend fun inviteTeman(...) = inviteFriend(...)
  ```
  Do not move files yet.
* Land with `version = 3` unchanged. CI green.

### Phase 1 — Introduce English as canonical (class renames, keep aliases) — 1 day

* Rename **class names inside files** but not file paths yet (lowest git churn):
  - `class TemanEntity → FriendEntity` with `@Entity(tableName="teman")` kept (Option A).
  - `class PendingEntity → PendingRequestEntity`.
  - `interface TemanDao → FriendDao`, `PendingDao → PendingRequestDao`.
  - Add deprecated typealiases reverse direction (§4.2) pointing new → old names.
  - Update `@Database(entities=[FriendEntity::class, PendingRequestEntity::class])` — include both old and new aliases if needed for schema export diff-0.
* Keep `@Query` strings on `teman` table literal.
* Keep file paths `presentation/teman/TemanFragment.kt` for now — only inner class name changes, import aliases handle call sites.
* Run `kspDebugKotlin` — confirm `FriendDao_Impl` generated alongside `TemanDao_Impl` (deprecated).

### Phase 2 — File & package moves via `git mv` (history-preserving) — 1 day, 3 commits

Commit 2a: `git mv mobile/app/src/main/java/com/upme/messenger/presentation/teman mobile/app/src/main/java/com/upme/messenger/presentation/friend`
* Rename package declaration `package com.upme.messenger.presentation.friend`.
* Rename file `TemanFragment.kt → FriendFragment.kt`, `class TemanFragment → FriendFragment`, keep `typealias TemanFragment = FriendFragment` in `LegacyAliases.kt`.

Commit 2b: `git mv presentation/profil → presentation/profile`
* `ProfilFragment.kt → ProfileFragment.kt`, `PrivasiKeamananFragment.kt → PrivacySecurityFragment.kt`.
* Same for `presentation/auth/SetupProfilFragment.kt → SetupProfileFragment.kt` (`SetupProfilFragment` → `SetupProfileFragment`).

Commit 2c: `git mv` layouts & sheet
* `res/layout/fragment_teman.xml → fragment_friend.xml`
* `res/layout/fragment_profil.xml → fragment_profile.xml`
* `res/layout/fragment_setup_profil.xml → fragment_setup_profile.xml`
* `res/layout/layout_sheet_invite_teman.xml → layout_sheet_invite_friend.xml`
* Update every `R.layout.*` reference in `FriendFragment.kt`, `ProfileFragment.kt`, `SetupProfileFragment.kt`, tests.

Each commit must be **mechanical rename only** (no logic change) and pass `./gradlew :app:assembleDebug`.

### Phase 3 — Navigation / menu / strings / call sites — ½ day

* `res/navigation/nav_graph.xml`: `<fragment android:id="@+id/nav_friend" android:name="com.upme.messenger.presentation.friend.FriendFragment" />` etc., keep old ids as shim in `res/values/ids.xml` if deep-link compat required.
* `res/menu/bottom_nav.xml`: `nav_friend` / `nav_profile`.
* `res/values/strings.xml`: introduce `empty_friend_*`, keep `empty_teman_*` as alias.
* `MainActivity.kt:21,49-50,94` → `import com.upme.messenger.presentation.friend.FriendFragment`, `R.id.nav_friend`, `titles = mapOf(R.id.nav_friend to "Teman", ...)`
* `ChatsFragment.kt:41-44`, `Detail`, `UpdatesFragment.kt:33` etc. any remaining `temanDao()` call sites → switch to `friendDao()` (keep deprecated path green).
* `TcpClient` call sites in `FriendFragment.kt:166,204,316` etc. → `inviteFriend`/`searchFriends`/`deleteFriend`.

### Phase 4 — DB migration finalization (pending 3→4 shim + optional 4→5) — ½ day

* **Pending migration contract** (see §6). Do NOT merge renaming into `MIGRATION_3_4`.
* Bump to `version = 5` only if Option B chosen, otherwise keep `version = 4` for English class names (since tableName unchanged, Room schema hash unchanged, no migration needed beyond 3→4). Verify with `./gradlew :app:kspDebugKotlin` + Room schema export diff.
* If Option A, the only version bump in this refactor is the already-pending `3 → 4` SQLCipher one; the English rename contributes **no extra migration** — document this explicitly in the migration file header.
* Wire `addMigrations(MIGRATION_3_4)` + (if B) `MIGRATION_4_5` in `UpMeDb.create()` at `UpMeDb.kt:170`.

### Phase 5 — Cleanup (next release, after 1–2 sprints) — ½ day

* Delete deprecated `typealias TemanEntity`, `TemanDao`, `TemanFragment`, `temanDao()` shims.
* Delete old string keys `empty_teman_*`, old `ids.xml` shims.
* Rename test `TemanForceCloseTest → FriendForceCloseTest`, keep `TemanForceCloseTest` as deprecated `class TemanForceCloseTest : FriendForceCloseTest()` for CI history if needed.
* Remove `fallbackToDestructiveMigration()` per `AUDIT_LOGIN_RELOGIN.md:6` and enable `exportSchema = true` + `AutoMigration` for future.

**Total effort**: ~3–4 days engineering + 1 day QA (2-device invite flow, kill + 24h persist, DB dump via `adb exec-out run-as com.upme.messenger cat databases/upme.db`).

---

## 6. Compatibility Shim for Pending Migration (3 → 4) — Critical Detail

**State of the repo as inspected**:

* Committed (`HEAD` in `/home/demo/UpMessengger/.git`, rev `3640ce3`): `UpMeDb.kt` version `3`, `create()` is `Room.databaseBuilder(...).fallbackToDestructiveMigration().build()` — no `Migration`, no SQLCipher, no `DbPassphrase`.
* Dirty working copy (`/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt:44-48,134,190-192`): `MIGRATION_3_4` (empty override), `version = 4`, full `DbPassphrase` + `migratePlaintextIfNeeded` + `SupportFactory(passphrase)`, plus English typealiases. This is **not committed** — `git --git-dir=/home/demo/UpMessengger/.git status` shows `UpMeDb.kt` modified, among 20+ files.
* Task phrase *"compatibility shim for pending migration"* refers to keeping this `3→4` SQLCipher migration working **while** class/table names are being renamed.

**Shim design — keep 3→4 untouched, isolate rename**:

1. **Do not edit `MIGRATION_3_4` at all during Phases 1–3.** It is `object : Migration(3,4) { override fun migrate(db: SupportSQLiteDatabase) {} }` — empty because schema columns didn't change, only the encryption layer changed via `SupportFactory`. If you rename `TemanEntity → FriendEntity` but keep `tableName="teman"` (Option A), Room's schema hash for version 4 stays identical → `MIGRATION_3_4` empty root remains correct. Any table rename would invalidate it.

2. **If you must rename the physical table (Option B), introduce a second migration `4 → 5`** instead of stretching `3→4`:

   ```kotlin
   val MIGRATION_4_5: Migration = object : Migration(4, 5) {
     override fun migrate(db: SupportSQLiteDatabase) {
       // Only if Option B chosen. Otherwise version stays 4 and this migration is omitted.
       db.execSQL("ALTER TABLE `teman` RENAME TO `friends`")
       // Alternative safe path: create new + copy + drop:
       // db.execSQL("CREATE TABLE `friends` (`pin` TEXT PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `status` TEXT NOT NULL)")
       // db.execSQL("INSERT INTO `friends` SELECT * FROM `teman`")
       // db.execSQL("DROP TABLE `teman`")
       // For pending_requests table, leave as-is or RENAME TO `pending_requests` identical → no-op.
     }
   }

   @Database(entities = [FriendEntity::class, PendingRequestEntity::class, MessageEntity::class],
             version = 5, exportSchema = true,
             autoMigrations = [])
   abstract class UpMeDb : RoomDatabase() { ... }
   ```

   Wire in `create()`:
   ```kotlin
   Room.databaseBuilder(ctx, UpMeDb::class.java, "upme.db")
     .openHelperFactory(factory)
     .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
     .fallbackToDestructiveMigrationOnDowngrade()
     // .fallbackToDestructiveMigration() — remove after shim period per AUDIT_LOGIN_RELOGIN.md
     .build()
   ```

3. **During shim period, `UpMeDb.create()` must handle 3 upgrade paths**:
   - Fresh install (no `upme.db` file) → directly creates `version = 4/5` encrypted DB, sets `db_encrypted` flag.
   - Existing plaintext `version 3` → `migratePlaintextIfNeeded()` runs `sqlcipher_export`, then Room sees `version 3` → runs `MIGRATION_3_4` (empty) → encrypted `version 4`.
   - Existing encrypted `version 4` pre-rename → Room runs `MIGRATION_4_5` (Option B) or no-op (Option A).
   The dirty-tree logic at `UpMeDb.kt:140-188` already probes `isEncryptedFlag` + `SQLiteDatabase.openDatabase` with hex key — **keep it verbatim**; add `MIGRATION_4_5` wiring after it.

4. **Testing the shim** (required before merge):
   ```bash
   # 1. Generate schema exports
   ./gradlew :app:kspDebugKotlin -P room.schemaLocation=mobile/app/schemas
   # 2. Install old APK (version 3, commit 3640ce3) on device, create a friend
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   # 3. Upgrade to new APK (version 4/5) without uninstall
   ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   # 4. Dump and verify
   adb exec-out run-as com.upme.messenger sqlite3 databases/upme.db "select name from sqlite_master where type='table';"
   # Expect (Option A): teman, pending_requests, messages  (unchanged)
   # Expect (Option B): friends, pending_requests, messages  (and view teman if created)
   adb exec-out run-as com.upme.messenger sqlite3 databases/upme.db "select * from teman limit 1;" # or friends
   ```

5. **Why not `AutoMigration` for this pending?**
   `AUDIT_LOGIN_RELOGIN.md:6` suggests `AutoMigration(3,4) + exportSchema true`. AutoMigration would work for pure class renames with `tableName` pinned, because schema unchanged. The current dirty tree uses manual `Migration(3,4) {}` — functionally identical. Either is fine, but **do not mix**: pick one for `3→4` and keep it. If you switch to `AutoMigration`, you must set `exportSchema = true` and commit `app/schemas/` JSON, and remove the manual `MIGRATION_3_4` object.

6. **Compatibility shim files to land in Phase 0** (so `main` stays green while PRs queue):
   ```kotlin
   // mobile/app/src/main/java/com/upme/messenger/data/local/LegacyAliases.kt
   @file:Suppress("DEPRECATION")
   package com.upme.messenger.data.local

   @Deprecated("Use FriendEntity", ReplaceWith("FriendEntity", "com.upme.messenger.data.local.FriendEntity"))
   typealias TemanEntity = FriendEntity

   @Deprecated("Use FriendDao", ReplaceWith("FriendDao", "com.upme.messenger.data.local.FriendDao"))
   typealias TemanDao = FriendDao

   @Deprecated("Use PendingRequestEntity")
   typealias PendingEntity = PendingRequestEntity

   @Deprecated("Use PendingRequestDao")
   typealias PendingDao = PendingRequestDao
   ```
   And `presentation/LegacyAliases.kt` for `TemanFragment`, `ProfilFragment`, etc.

---

## 7. Git History Preservation — Exact Commands

All commands assume `git --git-dir=/home/demo/UpMessengger/.git --work-tree=/home/demo/UpMessengger` (canonical repo). Sync `/home/demo/upchat` mirror after.

```bash
# Verify clean baseline
git status
git log --oneline -5   # 3640ce3, 0a89d22, ...

# Phase 2a — package teman → friend
git mv mobile/app/src/main/java/com/upme/messenger/presentation/teman \
       mobile/app/src/main/java/com/upme/messenger/presentation/friend
git mv mobile/app/src/main/java/com/upme/messenger/presentation/friend/TemanFragment.kt \
       mobile/app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt
# Edit FriendFragment.kt: package + class rename, keep typealias shim
git add -A && git commit -m "refactor(mobile): rename presentation/teman → friend, TemanFragment → FriendFragment (git mv, keep shim)

- Package com.upme.messenger.presentation.teman -> friend
- Class TemanFragment -> FriendFragment, typealias TemanFragment retained
- Update MainActivity, nav_graph, bottom_nav imports in next commit
- History: git log --follow -- friend/FriendFragment.kt shows TemanFragment.kt"

# Phase 2b — profil → profile
git mv mobile/app/src/main/java/com/upme/messenger/presentation/profil \
       mobile/app/src/main/java/com/upme/messenger/presentation/profile
git mv mobile/app/src/main/java/com/upme/messenger/presentation/profile/ProfilFragment.kt \
       mobile/app/src/main/java/com/upme/messenger/presentation/profile/ProfileFragment.kt
git mv mobile/app/src/main/java/com/upme/messenger/presentation/profile/PrivasiKeamananFragment.kt \
       mobile/app/src/main/java/com/upme/messenger/presentation/profile/PrivacySecurityFragment.kt
git mv mobile/app/src/main/java/com/upme/messenger/presentation/auth/SetupProfilFragment.kt \
       mobile/app/src/main/java/com/upme/messenger/presentation/auth/SetupProfileFragment.kt
git commit -m "refactor(mobile): rename presentation/profil → profile, Profil→Profile, PrivasiKeamanan→PrivacySecurity"

# Phase 2c — layouts
git mv mobile/app/src/main/res/layout/fragment_teman.xml mobile/app/src/main/res/layout/fragment_friend.xml
git mv mobile/app/src/main/res/layout/fragment_profil.xml mobile/app/src/main/res/layout/fragment_profile.xml
git mv mobile/app/src/main/res/layout/fragment_setup_profil.xml mobile/app/src/main/res/layout/fragment_setup_profile.xml
git mv mobile/app/src/main/res/layout/layout_sheet_invite_teman.xml mobile/app/src/main/res/layout/layout_sheet_invite_friend.xml
git commit -m "refactor(mobile): rename layouts fragment_teman/profil/setup_profil, layout_sheet_invite_teman (git mv)"
```

* Never `git rm + git add` — it breaks similarity detection below 50%. `git mv` keeps 100% rename detection for `git log --follow`.
* Keep each `mv` commit **without logic edits** so `git blame -C` still maps lines.
* After each commit: `git log --follow --name-status -- mobile/app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt | head` must show `R100` or similar.

---

## 8. Verification Checklist (must pass before merge)

- [ ] `git log --follow -- mobile/app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt` shows `TemanFragment.kt` history.
- [ ] `./gradlew :app:kspDebugKotlin :app:assembleDebug` — 0 errors, `UpMeDb_Impl.java` references `friendDao()` and deprecated `temanDao()`.
- [ ] `./gradlew :app:testDebugUnitTest` — `TemanForceCloseTest` still passes via alias, new `FriendForceCloseTest` added and green.
- [ ] Room schema: `mobile/app/schemas/com.upme.messenger.data.local.UpMeDb/4.json` (and `5.json` if Option B) committed, `tableName` for friends is `teman` (Option A) or `friends` (Option B) + queries on correct table.
- [ ] Migration test: install APK at `version=3` (tag `3640ce3`), insert 1 `teman` row, upgrade to new APK, assert row still readable via `friendDao().all()` and `temanDao()` alias.
- [ ] Manual 2-device flow (`ROADMAP_TEMAN_DAN_LOGIN.md`): HP A invite B → B `FriendFragment` shows pending, Accept → A sees `FriendEntity` without restart. `adb logcat` shows no `SQLiteException: no such table: teman`.
- [ ] `adb exec-out run-as com.upme.messenger sqlite3 databases/upme.db "PRAGMA user_version;"` reports expected version (4 or 5).
- [ ] Grep zero: `rg -n "temanDao\(\)|TemanEntity|TemanFragment|fragment_teman" mobile/app/src/main/java` returns only deprecated alias files after Phase 5.

---

## 9. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Pending SQLCipher migration `3→4` stepped on by table rename | Isolate rename to `4→5` (Option B) or keep table name (Option A) — §6 |
| `TemanForceCloseTest` hard-codes path `presentation/teman/TemanFragment.kt` | Keep typealias + wrapper test subclass; migrate test in Phase 5 |
| Deep-link `nav_teman` integer persisted in `NavController` savedState | Shim `ids.xml` alias (§4.4) |
| ProGuard/R8 shrinks deprecated aliases | Add `-keep class com.upme.messenger.data.local.LegacyAliases` and DAO shims in `proguard-rules.pro` until Phase 5 |
| Team in-flight branches | Land Phase 0 shims first, announce `git mv` window, provide `git merge -X rename-threshold=50%` hint |
| `fallbackToDestructiveMigration()` still present in dirty tree | Remove after Phase 4 verification — destructive fallback would hide a broken `MIGRATION_3_4` |

---

## 10. Automation Outline (optional helper script)

```bash
#!/usr/bin/env bash
# scripts/rename_english.sh — dry-run by default
set -euo pipefail
REPO=/home/demo/UpMessengger
cd "$REPO"
# 1. Verify git dir
git rev-parse --git-dir
# 2. Generate mapping from §3 and validate every old path exists
# 3. For each (old new): git mv "$old" "$new"
# 4. sed -i "s/package com.upme.messenger.presentation.teman/package com.upme.messenger.presentation.friend/g" ...
# 5. Run ./gradlew :app:kspDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "*TemanForceClose*"
```

* Script must not be run with `--yolo`; it calls `git mv` which respects approval policy for workspace writes.

---

## 11. Out of Scope / Not Renamed

* Backend Elixir (`lib/up_messenger/...`) — already English.
* DB file `upme.db`, prefs keys `upme_db_key`, `db_encrypted`, `SharedPreferences("upme")` — keep for downgrade compat.
* User-visible Indonesian copy ("Teman", "Profil", "Cari teman", "Privasi & Keamanan") — stays Indonesian; only code identifiers change.
* `UpMeDb` class name, `UpMeApp`, `ServiceLocator` — not Indonesian-derived.

---

## 12. Effort & Sequencing Summary

| Phase | What | Commits | Owner gate |
|---|---|---|---|
| 0 | Shims (typealias, TcpClient forwarders) | 1 | CI green |
| 1 | Class renames inside files | 1 | `kspDebugKotlin` green |
| 2a-c | `git mv` packages + files + layouts | 3 | `log --follow` verified |
| 3 | Nav/menu/strings/call sites | 1 | `assembleDebug` + install |
| 4 | Migration finalization (3→4 shim kept, optional 4→5) | 1 | migration test + 2-device |
| 5 | Cleanup aliases | 1 | next release |

Total **8 commits** across 5 phases, ~3–4 days. No downtime: each phase is shippable.

---

## 13. References (inspected)

* `mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt:15,27,37,44-48,134,190-192` — current schema + pending `MIGRATION_3_4` + alias shim.
* `mobile/app/src/main/java/com/upme/messenger/presentation/teman/TemanFragment.kt:1,44,113,166,204,293,354`
* `mobile/app/src/main/java/com/upme/messenger/presentation/profil/ProfilFragment.kt:32` + `PrivasiKeamananFragment.kt:29` + `auth/SetupProfilFragment.kt:21`
* `mobile/app/src/main/res/navigation/nav_graph.xml:9-11` + `res/menu/bottom_nav.xml:5-6` + `res/layout/fragment_teman.xml` / `fragment_profil.xml` / `layout_sheet_invite_teman.xml`
* `mobile/app/src/test/java/com/upme/messenger/presentation/teman/TemanForceCloseTest.kt:8-12`
* `mobile/app/src/main/java/com/upme/messenger/presentation/MainActivity.kt:13-14,21,49-50,94`
* `mobile/app/src/main/java/com/upme/messenger/data/net/TcpClient.kt:99,126,166`
* Committed HEAD: `git --git-dir=/home/demo/UpMessengger/.git show HEAD:mobile/app/src/main/java/com/upme/messenger/data/local/UpMeDb.kt` (version 3).
* Dirty diff: `git --git-dir=/home/demo/UpMessengger/.git diff` — shows `UpMeDb.kt` +80 lines for SQLCipher 3→4.
* Docs: `docs/AUDIT_LOGIN_RELOGIN.md:6,14`, `docs/ROADMAP_TEMAN_DAN_LOGIN.md`, `docs/STANDAR_UI_REQUIREMENTS.md` (layout/id naming norms), `STANDARD.md` (4 GB RAM constraints).
