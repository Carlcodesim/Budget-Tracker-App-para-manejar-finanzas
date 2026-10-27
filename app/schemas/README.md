# Room schema snapshots

This directory is the configured output location for Room schema JSON files.

After running the Android build with KSP configured, commit the generated schema file(s), especially:

- `com.example.data.local.AppDatabase/12.json`

Do not fabricate historical schema files. Versions 1–11 must come from historical builds, archived APKs, or trustworthy database backups before they are used to implement migration paths.

Before changing or removing `.fallbackToDestructiveMigration(true)`, verify all upgrade paths used by installed releases and add migration tests that preserve representative financial records.
