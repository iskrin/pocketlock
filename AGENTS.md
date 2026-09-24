# AGENTS.md

## Remote operations — ALWAYS ASK FIRST

**Never push anything to the GitHub repository (`iskrin/pocketlock`) or create/modify GitHub
Releases without the user's explicit approval.** Ask first, wait for confirmation, then proceed.

This includes:
- `git push` (any branch) and pushing tags (`git push origin <tag>`)
- creating, editing or deleting GitHub Releases
- uploading or replacing release assets (APKs)
- any GitHub API call that modifies the remote

Local work is fine without asking: editing files, building APKs, `git add`, local commits,
running the app's build/tests.

## Project

**Pocket Lock** — a custom Android lock screen for the Retroid Pocket Nova (Android 13, 4:3).
Unlock by pressing any button (or tapping the screen) 3 times. Kotlin, no external
dependencies, minSdk 27, targetSdk 34, compileSdk 35.

Key components: `LockService` (foreground service + screen on/off receiver), `LockOverlayView`
(dots, press counting, exit animation), `LockActivity` (fallback above a system keyguard),
`AppearanceActivity` (background/dots/colors), `LockAppearance` (shared rendering).

## Build

Tooling lives in `%LOCALAPPDATA%\Android` (JDK 17, Android SDK, Gradle 8.11.1).

```
$env:JAVA_HOME="$env:LOCALAPPDATA\Android\jdk17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
& "$env:LOCALAPPDATA\Android\gradle-8.11.1\bin\gradle.bat" assembleRelease assembleDebug --no-daemon
```

Output: `app\build\outputs\apk\release\app-release.apk` → copy to `dist\PocketLock.apk`
and `dist\PocketLock-<version>.apk` (used for releases).

## Release process (only after the user approves)

1. Bump `versionCode` in `app/build.gradle.kts` — it must be higher than any previously
   released or locally installed build (the console refuses downgrades). Set `versionName`
   to the public version (e.g. `1.0`).
2. Build and verify: `apksigner verify dist\PocketLock-<version>.apk`.
3. Commit locally.
4. **Ask the user for permission**, then push and create the GitHub Release
   `v<versionName>` with the APK attached as `PocketLock-<versionName>.apk`.
   Keep old releases — they are the archive of older versions.

## Notes

- `keystore/` and `keystore.properties` are gitignored — never commit secrets.
- `dist/` is gitignored; APKs are distributed through GitHub Releases, not the repo.
- Remote auth on this machine: stored HTTPS credential for github.com (SSH key is not
  registered on GitHub), so `gh` is unavailable — use `git` and the GitHub REST API
  (token can be read with `git credential fill`).
