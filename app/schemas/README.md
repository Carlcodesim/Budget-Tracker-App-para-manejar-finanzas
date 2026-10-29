# Room schema snapshots

This directory is the configured output location for Room schema JSON files.

After running the Android build with KSP configured, commit the generated schema file(s), especially:

- `com.example.data.local.AppDatabase/12.json`

## APK audit notes

An uploaded APK named `base.apk` was inspected on 2026-10-27. SHA-256:

`3121e2c97089f5804987410546cce73c6814dfa44e5ffa0bcd94a13214372081`

The APK contains Room's generated `AppDatabase_Impl` and the app migration implementations for 7→8, 9→10, 10→11, and 11→12. Its current Room schema includes these seven app tables: `transactions`, `categories`, `savings_goals`, `reminders`, `accounts`, `recurring_expenses`, and `businesses`. The generated implementation contains current identity hash `86254750241babac4b8d52996a675549` and legacy hash `16ac758fb3645c784d1e2c847837bdf5`.

This is evidence about the APK's current generated schema. The current schema SQL in the APK matches the seven tables and columns represented by the current Room entities in source. The recent merged transfer-atomicity PR changes transaction insertion logic, repository/use-case code, tests, and CI configuration; it does not change `AppDatabase`, entity definitions, the Room version, or migration SQL, so it does not itself require a Room schema-version bump.

The APK does not contain an app migration 8→9. The legacy identity hash is Room's compatibility hash, not a historical schema export; it is not enough to safely infer the exact version-11 schema or write migration SQL.

## Safety rules

- Do not fabricate historical schema files. Versions 1–11 must come from historical builds, archived APKs, or trustworthy database backups before they are used to implement migration paths.
- A safety change on this branch removes `.fallbackToDestructiveMigration(true)`. If Room cannot find a migration path, opening the database should fail instead of silently dropping the database. This is a data-preservation improvement, but it is **not release-ready by itself**: older users may be unable to open the app until a recovery flow is implemented and tested.
- `DestructiveMigrationSafetyTest.kt` creates a synthetic v8 SQLite database with a sentinel row and checks that an unsupported migration path throws without deleting the file's data. This test does not claim to reconstruct the full v8 schema. It has been added but has not yet been executed in this audit.
- The JSON backup previously omitted `businesses` and restored records through separate DAO writes. On this audit branch, `BackupManager.kt` now exports/imports businesses and wraps restoration in `RoomDatabase.withTransaction`, so a failed restore should roll back all database writes. Tests were added for business export and rollback on malformed input; their execution result must be checked in CI before considering the change verified. This JSON still does not include every possible piece of app state (for example, SharedPreferences), so it is a data backup, not a byte-for-byte database copy.
- Before releasing this safety change, implement a user-friendly recovery path for unsupported versions and add migration tests for the known 9→12 route using verified v9 schema evidence.
- Keep this audit on its own branch; do not change `main` or merge a PR without explicit approval.
