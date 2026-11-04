# Strict database quarantine and recovery plan

## Scope and safety contract

This plan applies to the Budget Tracker Android application. The original database is immutable while quarantined. If its integrity, schema version, or consistency cannot be certified, the app must not open it through Room, migrate it, repair it, rename it, replace it, or fall back to a newly created database at the original path.

No normal screen, worker, receiver, widget, scheduler, or repository may bypass the database access gate.

## Historical baseline before this feature branch

- Audited branch: `audit/room-migration-safety`
- Baseline commit: `d3c4ecec0252ff7819c4027ef065ec02cecf58d0`
- Room schema version: 14
- Original database filename: `budget_tracker_database`
- The Room builder registers migrations 7->8 and 9->14; migration 8->9 is absent.
- At that baseline, startup observers, schedulers and the balance widget could reach Room without a common authorization gate.
- Existing JSON export/import uses DAOs and is not a physical SQLite backup.

## Current implementation status

- The feature branch contains a deny-by-default access gate, read-only preflight, immutable raw snapshot copying, isolated SQLite/Room validation, read-only financial auditing, explicit activation, durable active-copy registry, and isolated migration rehearsal for schemas 9–13.
- The current schema is v14. Schemas 1–8, future versions and failed/inconsistent evidence remain quarantined.
- The original database path is not a Room target. Normal Room access is restricted to a validated private copy after explicit authorization.
- The branch has not been merged. Latest-head CI and a complete review of the full branch delta are still release gates.

## Approved invariants

1. Unknown, corrupt, inconsistent, future-version, or uninspectable originals enter strict quarantine.
2. The original database is never opened through Room while quarantined.
3. Recovery operates on an independent copy; never on the original path.
4. A partial or unverified copy is never certified or activated.
5. Activation and user authorization are distinct states.
6. A missing active database must not cause automatic creation of an empty replacement.
7. Workers, receivers, widgets, UI, schedulers, and repositories obey the same access gate.
8. No automatic destructive migration or fallback is permitted.
9. Do not log financial records, passwords, recovery phrases, keys, or decrypted backup contents.
10. Until physical snapshot and activation are proven safe, fail closed.

## State and permission vocabulary

Storage classification:
- ABSENT
- SUPPORTED
- LEGACY_UNSUPPORTED
- FUTURE_VERSION
- UNKNOWN
- CORRUPT
- INCONSISTENT
- IO_ERROR

Recovery phase:
- PREFLIGHT
- BACKUP_REQUIRED
- SNAPSHOTTING
- VALIDATING_SNAPSHOT
- SNAPSHOT_CERTIFIED
- RECOVERY_REQUIRED
- MIGRATION_REHEARSAL
- READY_FOR_AUTHORIZATION
- SAFE_MODE

Permission:
- NO_ACCESS
- SNAPSHOT_ONLY
- RECOVERY_COPY_ONLY
- NORMAL_ACCESS

Only a fully validated, financially clean, explicitly authorized active copy may receive NORMAL_ACCESS. The coordinator persists the active-copy pointer before granting the in-process gate; each new process re-snapshots and revalidates that registered generation before reopening it.

## Implementation stages

### Stage 0 — Baseline and access-path audit
- Enumerate every direct and indirect Room access.
- Inspect merged manifest and startup initializers.
- Keep original branch and financial data untouched.

### Stage 1 — Access gate and safe startup
- Define the central permit API and startup evaluation.
- Ensure the gate is evaluated before any Room instance or database operation can be obtained.
- Block initialization observers, schedulers, workers, receivers, and widget paths unless permitted.
- Add tests proving zero Room acquisition in NO_ACCESS and SAFE_MODE.
- Do not claim this stage complete until all bypasses are removed and tests pass.

### Stage 2 — Snapshot protocol
- Quiesce all database users.
- Create an independent, consistent SQLite snapshot including relevant sidecars when applicable.
- Validate the copy in isolation.
- Certify only after integrity checks and copy verification pass.
- Inject interruption, disk-full, corruption, and WAL scenarios.

### Stage 3 — Recovery copy and activation journal
- Migrate only a certified independent copy.
- Validate schema, integrity, and essential data.
- Publish active database registry atomically with crash-recovery journal.
- Keep activation separate from authorization.
- Never fall back to the original or silently create a replacement.

### Stage 4 — Encrypted .btr format
- Use reviewed Android-compatible cryptographic libraries; no custom cryptography.
- Enforce bounded, versioned headers and authenticated chunk metadata.
- Verify password and recovery-key envelopes, tamper detection, truncation rejection, and hostile parameter limits.
- Verify exported ciphertext by reading it back from the destination.

### Stage 5 — Release criteria
- All required unit, integration, interruption, crypto, and device tests pass.
- Merged manifest and backup/restore behavior are audited.
- Historical database compatibility is supported by authentic fixtures or explicitly classified unsupported.
- No unresolved path can reach Room before authorization.

## Initial acceptance tests

- T-01 corrupt original: safe mode, zero Room opens, original unchanged.
- T-02 future schema version: FUTURE_VERSION, zero Room opens.
- T-03 interrupted snapshot: no certification or activation.
- T-04 storage full: fail closed, original unchanged.
- T-05 process killed at every persisted phase: safe restart, no partial activation.
- T-06 committed WAL changes: snapshot includes all committed changes.
- T-07 worker invoked during quarantine: no Room access or writes.
- T-08 widget update during quarantine: no direct Room access.
- T-09 activation interrupted: pending journal detected; no NORMAL_ACCESS.
- T-10 copy hash mismatch: reject copy; preserve original.

## Completion reporting

For each stage, report:
- files changed and why;
- tests added and command/run results;
- CI status;
- remaining risks;
- explicit confirmation that no merge to the default branch occurred.

Do not mark a stage complete based only on successful compilation. Do not merge to the default branch without explicit user authorization.


## Implementation log

### Stage 1A — Pure access-policy core (implemented)

- Added `app/src/main/java/com/example/data/recovery/StrictDatabaseAccessPolicy.kt`.
- Added `app/src/test/java/com/example/data/recovery/StrictDatabaseAccessPolicyTest.kt` with eight decision-policy tests.
- Added the `feature/**` branch pattern to Android CI push triggers so isolated implementation branches can run the existing unit-test and debug-build workflow.
- These changes define and test policy decisions, but they do **not** yet prevent Room from opening the original database. `AppDatabase`, `AppContainer`, application startup, workers, receivers, and the widget still need to be routed through an enforced gate.
- Stage 1 remains incomplete until the gate is wired before every Room acquisition, all bypasses are removed, and the acceptance tests pass.

### Stage 1B — Room acquisition and runtime entry-point guard (implemented; final manifest/process audit pending)

- Added a process-wide `DatabaseAccessGate`; its default state is `NO_ACCESS` on every process start.
- `AppDatabase.getDatabase()` now requires an authorized file path before consulting its singleton or constructing Room.
- Room's configured filename is now the exact authorized recovered-copy path, not the legacy `budget_tracker_database` path.
- The gate rejects paths outside the app-private `filesDir`, empty/missing files, and the legacy original database path. It rechecks the selected file at acquisition time.
- A different database path cannot reuse an already-open Room singleton; a controlled close/quiesce/generation switch is still required.
- `BudgetTrackerApp` no longer starts database observers or database-backed schedulers. `MainActivity` shows a recovery-safe screen without constructing ViewModels or navigating to financial screens.
- The balance widget, recurring-expense scheduler, workers, and database-backed broadcast receivers now stop while the process has no normal database permit.
- Added gate tests and a Robolectric test asserting a denied Room acquisition does not create the legacy database file; additional tests reject the original path and exercise the private-copy path check.
- The runtime gate is now connected to the recovery coordinator and explicit-authorization screen documented in Stage 3A. Remaining release work is the merged-manifest/process audit, device-level verification, and complete latest-head CI.
- CI is now configured for `feature/**` pushes. Latest run is being evaluated; no test or build result is claimed until GitHub Actions finishes.


### Stage 1C — Read-only SQLite preflight (implemented; header recognition only)

- Added `DatabasePreflightInspector.kt`. It reads only the first 100 bytes of the original SQLite file and checks filesystem sidecar presence. It does not use Room, Android SQLite APIs, or a writable file descriptor.
- It checks SQLite magic, page-size constraints, SQLite read/write format versions, reserved bytes, payload fractions, encoding, minimum file size, page alignment, and the declared page count.
- It classifies schema 14 as `HEADER_RECOGNIZED`, **not** `SUPPORTED`. A recognized header is not proof of `quick_check`, `integrity_check`, schema identity, or financial consistency.
- Versions 9–13 are classified as migration candidates only; the registered migrations must still be rehearsed on an independent copy. Versions 1–8 are conservatively unsupported because the registered path has a known gap at 8→9 (and earlier versions have no complete registered chain). Future versions are quarantined.
- Any WAL/SHM sidecar prevents treating the main DB file alone as a complete snapshot. A rollback journal is classified inconsistent. The snapshot copier carries WAL/journal into an isolated workspace; the validator performs SQLite recovery/checkpoint validation there. SHM is observed but never copied.
- `BudgetTrackerApp.onCreate()` publishes the read-only preflight report before creating the lazy dependency container. The safe screen displays the classification and sidecar hints; this still does not grant Room permission.
- Added `DatabasePreflightInspectorTest.kt` for absent files, valid current headers, future schema, migration candidates, known migration gaps, WAL and journal sidecars, invalid signatures, truncated headers, and byte/hash preservation.
- No original database bytes are written or migrated by the inspector. It must not be used as a substitute for SQLite integrity checking on a validated independent copy.


### Stage 2A — Raw private snapshot copier (implemented as evidence capture; not certified)

- Added `DatabaseSnapshotCopier.kt` to copy the original main database plus present `-wal` and `-journal` files into a unique directory below app-private `filesDir/recovery/snapshots`.
- A process-local file lock prevents two snapshot attempts through this copier from overlapping.
- Copies are streamed, flushed and fsynced. SHA-256 and size are checked against the source. The durable source set is hashed before and after copying; any detected change or sidecar appearance/disappearance rejects the snapshot and removes the partial copy.
- `-shm` is recorded as observed but not copied because it is transient WAL-index/locking state; the isolated copy must reconstruct its own SHM.
- The manifest explicitly records `integrity_certified=false` and `normal_access_authorized=false`. The returned result also carries both flags as false.
- The copier refuses to run while normal database permission is active or while an AppDatabase Room singleton has been constructed in this process.
- Added Robolectric tests for missing original DB, byte-identical main+WAL copy, preserving-but-not-copying SHM, rollback journal copy, source immutability, and independent snapshot IDs.
- This is a raw copy mechanism, not a complete recovery/snapshot certification protocol. It does not yet provide a durable snapshot journal, disk-full fault injection, process-death recovery, isolated SQLite integrity checks, WAL/journal recovery validation, or proof of system-wide quiescence. It is not called automatically and must not be treated as an authorized active database.


### Stage 2B — Isolated SQLite and Room-schema validation (implemented; latest-head CI pending)

- Added `DatabaseSnapshotValidator.kt`. It first verifies the raw snapshot manifest, snapshot ID, expected file locations, SHA-256 hashes, byte sizes and sidecar metadata.
- It rejects symlinked snapshot components and any SHM file inside the raw snapshot. The copier's contract is to record SHM presence but not copy the transient SHM index.
- The validator creates a second private validation workspace and copies the raw database, WAL and rollback journal into it. SQLite is opened read-write **only on this disposable workspace**, with `CREATE_IF_NECESSARY` omitted. The original database and raw snapshot are not opened or modified.
- SQLite checks run on the workspace: `PRAGMA quick_check(1)`, `PRAGMA integrity_check`, `user_version = 14`, expected Room identity table/identity presence, exact expected table columns, declared types, nullability, primary keys, selected SQL defaults, and required Room index names/columns.
- When a copied WAL exists, the validator requires a successful `PRAGMA wal_checkpoint(TRUNCATE)` with no busy frames and rejects an unresolved non-empty rollback journal or WAL payload after closing SQLite.
- Validation writes a separate report with pre-/post-validation hashes and explicit scope markers: SQLite checks/schema pass, financial consistency not yet validated, and Room access not authorized.
- The raw snapshot is preserved byte-for-byte. SQLite recovery/checkpoint writes can only affect the second-generation validation workspace.
- Added tests for a valid Room-shaped database, corrupted SQLite, a missing required index, future schema version, tampered raw snapshot, and committed WAL data visible in the validated workspace while original/raw snapshot bytes remain unchanged.
- **Scope boundary:** these checks certify SQLite structural integrity, the expected v14 Room table/index layout, and the generated Room identity hash on an isolated copy. They do not validate financial/business invariants; that is the separate Stage 2C audit. No migration or activation is performed by this validator.
- The validation workspace is not automatically selected as the active DB. No production code grants `DatabaseAccessGate` permission from this report.


### Stage 2C — Read-only financial/business consistency scan (implemented; latest-head CI pending)

- Added `DatabaseFinancialConsistencyAuditor.kt`. It only accepts a `SnapshotValidationResult.Validated` whose validation report matches the snapshot/workspace identifiers, schema version 14, successful SQLite and schema checks, and a SHA-256 hash that still matches the validated database.
- The auditor enforces app-private workspace paths, rejects symlinked inputs, refuses if normal Room access is enabled, and opens the validated workspace using `SQLiteDatabase.OPEN_READONLY`. It never opens the original DB or raw snapshot.
- It examines transaction amounts and types, account/category references, category/type compatibility, correlated transfer groups, category budgets/types, savings-goal amounts, reminder dates/amounts/goal references, recurring amount/day/frequency/month/account/category data, and business names/inventory values.
- Transfer pairing is checked only where a `CORR:<id>` correlation token exists. The expected structure is exactly one `OUT` and one `IN`, different accounts, same date and exact amount. Uncorrelated transfer-like legacy records and anomalous groups are review findings, not automatic repairs.
- Savings goals whose current amount exceeds their target are classified as `REVIEW`; the stored amounts are preserved exactly. The auditor also preserves all other financial values and emits findings with table, row ID, rule code, severity and a neutral explanation; it deliberately avoids copying transaction amounts or business-sensitive descriptions into the report.
- The financial report is a separate atomic `.properties` file in the validation workspace. The database SHA-256 is compared before and after the read-only scan. The report explicitly records that Room/normal access is not authorized.
- Added Robolectric coverage for a clean database, multiple inconsistent records (including an overshot savings goal and an incomplete correlated transfer), byte/hash preservation, and rejection when the validated database changes after structural validation.
- **Scope boundary:** a completed scan means the defined consistency checks ran; it is not a guarantee that all user-specific business rules are correct. Findings require human review and the scan does not fix data. The production recovery coordinator now invokes this auditor after structural validation; only a zero-finding report can proceed to a separate explicit-authorization step. The auditor itself never grants access.


### Stage 3A — Strict recovery coordinator and explicit activation (implemented; latest-head CI pending)

- Added `StrictRecoveryCoordinator` to orchestrate read-only preflight, raw snapshot, isolated SQLite/Room validation, and financial consistency scan. A structurally and financially clean v14 database may reach the explicit confirmation stage. Supported legacy v9-v13 databases first require a separate migration rehearsal on a disposable second-generation copy (Stage 3B).
- Any finding from the financial audit stops activation and returns a manual-review result. Damaged, unknown, future and unsupported legacy versions remain blocked. Migration is never run against the original database or immutable raw snapshot; only the isolated rehearsal copy is changed.
- Activation rechecks the validation and financial reports, workspace boundaries, schema version, report flags and database hash. It registers the exact private active copy before asking `DatabaseAccessGate` for a normal-access permit.
- Added `ActiveDatabaseCopyRegistry`. After the first explicit activation, subsequent launches use that registered private DB as the next snapshot source, not the stale historical database path. If the registry disappears or becomes invalid, the app fails closed rather than silently reverting to an older financial generation.
- The registry writes an irreversible intent marker both inside `files/recovery/` and separately under `noBackupFilesDir/`. If the entire recovery workspace is lost, the second marker still prevents silent fallback to the historical database path. A regression test deletes the whole recovery folder and verifies fail-closed behavior.
- Added an isolated first-install bootstrap. Only when the original database and all its sidecars are absent, a new v14 database may be created under `files/recovery/bootstrap/<UUID>/`, initialized with standard categories and accounts, then copied, validated and audited. It never creates the historical database path and does not seed businesses or recurring expenses.
- The main activity now exposes a separate user confirmation before activation. The existing navigation and PIN protection return only after the gate confirms the activated path. Database-dependent widget, reminder and recurring schedulers restart only after authorization; workers, receivers and widget entry points remain gated.
- Android Auto Backup / device transfer excludes the legacy database domain and `files/recovery/`. This prevents an OS-level restore from pairing an old database with a missing or mismatched active-copy registry. Financial data must be backed up through the versioned JSON export/import feature; users should export a fresh JSON backup before changing devices or reinstalling.
- Added integration tests for first install, explicit authorization, no preseeded businesses/recurring expenses, active-copy selection after a simulated process restart, and refusing silent fallback when the active registry is missing.

### Stage 3B — Disposable legacy migration rehearsal (implemented; authentic historical-fixture coverage pending)

- Added `DatabaseMigrationRehearsal`. It accepts only raw snapshots whose SQLite `user_version` is 9–13 and whose preflight classification is a known migration candidate (or a supported WAL/rollback-journal snapshot case). Versions 1–8 remain unsupported because the registered Room migration chain has a gap from 8 to 9.
- The rehearsal verifies the raw snapshot manifest, path boundaries, hashes, sidecar metadata and quarantine flags, then copies its database/WAL/journal into a distinct private `recovery/migrations/<UUID>/` workspace. Room receives only this migration copy and the registered non-destructive migrations. No destructive fallback is configured.
- After migration, the rehearsal closes Room and independently checks the resulting schema version 14, SQLite quick/integrity checks and Room identity presence. It verifies the raw snapshot hashes again and writes a separate report that explicitly says `normal_access_authorized=false` and `room_authorized=false`.
- `DatabaseSnapshotValidator.validateMigrated` does not trust the migrated workspace as the final database. It verifies its report, location and hashes, then copies it into a new `recovery/validation/<UUID>/` workspace and performs the complete generated Room schema, SQLite integrity and identity-hash validation again. The financial auditor scans only that final validated workspace.
- Added synthetic end-to-end fixtures for every supported source schema version v9, v10, v11, v12 and v13. They check transaction and recurring-expense preservation, category and index migrations, business-table introduction, the 13→14 untouched placeholder removal, user-created business preservation, source/raw-snapshot hash preservation, and no authorization from migration or validation. These fixtures are reconstructed from the current schema and migration definitions; they are not archived databases produced by actual historical app releases. Authentic historical release fixtures and device-level upgrade tests remain outstanding.
- Migration workspaces are not automatically activated. A clean final validation and financial report are still required, followed by explicit user approval. Failed rehearsal outputs are not used and normal access remains denied.

### Current release boundary / not yet a merge approval

- The coordinator supports the exact current schema v14 and attempts a disposable migration rehearsal for versions 9–13. Versions 1–8, future versions, unknown schemas, malformed sidecars and all failed or inconsistent evidence remain quarantined. The v13→14 migration now removes only the deterministic placeholder inserted by the known 11→12 migration (including its fixed timestamp). An older onOpen-created lookalike with a variable timestamp is preserved and classified for human review because its ownership cannot be established safely. Do not treat this branch as compatible with every historical database.
- CI has passed for the preceding revisions that introduced v9–v13 synthetic migration tests. The latest change that moves the durable active-copy marker outside `recovery/` is still awaiting its own latest-head unit-test and debug-APK workflow. The branch must not be merged until the exact latest-head workflow passes.
- A final review must inspect the full branch delta (including recurring-expense, backup/import and UI changes), every direct database entry point and background component, Room migration coverage, first-install/restore behavior, and Android backup behavior. The branch is not merged and `main` remains untouched.
