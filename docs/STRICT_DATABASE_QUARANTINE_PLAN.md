# Strict database quarantine and recovery plan

## Scope and safety contract

This plan applies to the Budget Tracker Android application. The original database is immutable while quarantined. If its integrity, schema version, or consistency cannot be certified, the app must not open it through Room, migrate it, repair it, rename it, replace it, or fall back to a newly created database at the original path.

No normal screen, worker, receiver, widget, scheduler, or repository may bypass the database access gate.

## Current audited baseline

- Audited branch: `audit/room-migration-safety`
- Baseline commit: `d3c4ecec0252ff7819c4027ef065ec02cecf58d0`
- Room schema version: 14
- Original database filename: `budget_tracker_database`
- The Room builder registers migrations 7->8 and 9->14; migration 8->9 is absent.
- `BudgetTrackerApp.onCreate()` starts data observers and scheduling paths that can access Room before the user enters the main UI.
- `AppContainer.database` lazily constructs the Room database without an authorization gate.
- `BalanceWidgetProvider` has a direct Room access path separate from `AppContainer`.
- Existing JSON export/import uses DAOs and is not a physical SQLite backup.
- No code change to runtime behavior is made by this plan document.

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

Only a fully validated, explicitly authorized active copy may receive NORMAL_ACCESS. The exact persisted transition protocol must be implemented and tested before runtime wiring is considered complete.

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

### Stage 1A — Pure access-policy core (in progress; not runtime enforcement)

- Added `app/src/main/java/com/example/data/recovery/StrictDatabaseAccessPolicy.kt`.
- Added `app/src/test/java/com/example/data/recovery/StrictDatabaseAccessPolicyTest.kt` with eight decision-policy tests.
- Added the `feature/**` branch pattern to Android CI push triggers so isolated implementation branches can run the existing unit-test and debug-build workflow.
- These changes define and test policy decisions, but they do **not** yet prevent Room from opening the original database. `AppDatabase`, `AppContainer`, application startup, workers, receivers, and the widget still need to be routed through an enforced gate.
- Stage 1 remains incomplete until the gate is wired before every Room acquisition, all bypasses are removed, and the acceptance tests pass.
