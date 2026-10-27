# Room schema snapshots

This directory is the configured output location for Room schema JSON files.

After running the Android build with KSP configured, commit the generated schema file(s), especially:

- `com.example.data.local.AppDatabase/12.json`

## APK audit notes

An uploaded APK named `base.apk` was inspected on 2026-10-27. SHA-256:

`3121e2c97089f5804987410546cce73c6814dfa44e5ffa0bcd94a13214372081`

The APK contains Room's generated `AppDatabase_Impl` and the app migration implementations for 7→8, 9→10, 10→11, and 11→12. Its current Room schema includes these seven app tables: `transactions`, `categories`, `savings_goals`, `reminders`, `accounts`, `recurring_expenses`, and `businesses`. The generated implementation contains current identity hash `86254750241babac4b8d52996a675549` and legacy hash `16ac758fb3645c784d1e2c847837bdf5`.

This is evidence about the APK's current generated schema, but it does not provide the missing historical schema definitions. The APK also does not contain an app migration 8→9. The legacy identity hash alone is not enough to safely infer the exact version-11 schema or write migration SQL.

## Safety rules

- Do not fabricate historical schema files. Versions 1–11 must come from historical builds, archived APKs, or trustworthy database backups before they are used to implement migration paths.
- Before changing or removing `.fallbackToDestructiveMigration(true)`, verify all upgrade paths used by installed releases and add migration tests that preserve representative financial records.
- Keep this audit on its own branch; do not change `main` or merge a PR without explicit approval.
