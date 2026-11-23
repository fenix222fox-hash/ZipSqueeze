# ZipSqueeze

Native Android ZIP extractor using the platform `java.util.zip` streaming APIs and the Storage Access Framework. The current phone build toolchain only supports plain Java and Android framework APIs (no Gradle, Kotlin, AndroidX, Compose or third-party libraries), so this project deliberately uses a small native Java UI.

## Current features

- Select a ZIP through Android's document picker and a destination through the folder picker (including SAF providers that support writing).
- Extract entries on a worker thread with a bounded 64 KiB buffer; stream entry data instead of loading the archive in memory.
- Reject absolute paths and `..` path traversal entries.
- `ZipInputStream` checks entry CRC when an entry reaches end of stream. On an error or cancellation, the source archive is kept.
- Preview up to 250 entry names; open ZIP files through Android's share/open intent.
- Optional source deletion only after all entries finish and CRC checks succeed.

## Important storage limitation

This build does **not** progressively free the source ZIP's occupied blocks. Standard ZIP entries may be interleaved with the central directory and arbitrary later entries; truncating or zeroing bytes is not safe and does not necessarily return filesystem blocks. Rewriting the remaining ZIP requires temporary space. The app preserves source data on failed extraction and deletes it only after successful completion when the user selects that option. It cannot promise extraction of a 5 GB archive with only 2 GB free when the expanded output exceeds available storage.

## Not implemented in this build

Kotlin/Compose, Hilt, Room, WorkManager/foreground notifications, crash resume, Shizuku, encrypted ZIPs, RAR/7z/TAR/GZ/BZ2/XZ, SHA-256 input, progress estimation/minimum-space preflight, and automated test coverage. These require a full Android Gradle toolchain and dependencies; the phone-specific build environment documented by the workspace does not provide them. Do not use this build for password-protected archives.

## Build

From the Buddy workspace, run:

```sh
bash .claude/skills/android-app/build-apk.sh "$PWD/ZipSqueeze"
```

Output: `ZipSqueeze/build/app.apk`.
