# Xtream TV — Android TV / Fire TV

A native IPTV player for **Android TV, Google TV and Amazon Fire TV** (Fire Stick, Fire TV Cube, Fire TV
televisions) that signs in to an **Xtream Codes** provider. Built for the remote: everything works with
the D-pad, OK and Back.

The Xtream API handling is a Kotlin port of the logic in
[tbeezy/ynotv](https://github.com/tbeezy/ynotv) (`xtream-client.ts`): the same `player_api.php` actions,
stream/timeshift URL shapes and tolerance for the odd JSON real providers return. The ynotv UI itself is a
Windows desktop app (Tauri + mpv) and can't run on a TV, so the player here is Media3/ExoPlayer.

## Features

- Sign in with server URL + username + password (or paste a full `get.php?username=…&password=…` link)
- **Live TV** – categories, channel numbers, search, now/next guide, Up/Down (or CH+/CH−) to zap channels
- **Movies** and **Series** – poster grid, search, seasons/episodes, auto-play next episode
- Resume position for movies/episodes, seek ±10 s with Left/Right, pause with OK
- Prefers HLS (`m3u8`) when your account allows it and falls back to `ts` automatically
- Account info (expiry, connections), reload lists, sign out

## Install on a Fire TV Stick (sideload)

1. Fire TV: **Settings → My Fire TV → Developer options** → turn on **Apps from Unknown Sources**
   (and **ADB debugging** if you'll use `adb`).
2. Get the APK from the **Actions** tab → latest run → artifact `xtream-tv-apk`
   (use `app-release.apk`), or build it yourself (below).
3. Install either with `adb`:
   ```
   adb connect <firestick-ip>:5555
   adb install -r app-release.apk
   ```
   or with the **Downloader** app on the Fire TV using a link to the APK.

On Android TV / Google TV the same APK installs the same way (`adb install`, or "Send files to TV").

## Build

Requires JDK 17, Gradle 8.14+ and the Android SDK (API 35).

```
gradle :xtream:test            # Xtream client unit tests (runs on any JVM)
gradle :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk (debug-signed, sideloadable)
```

GitHub Actions builds both APKs on every push (`.github/workflows/android.yml`).

## Layout

| Path | What |
| --- | --- |
| `xtream/` | Pure-Kotlin Xtream Codes client, models, URL builders + tests |
| `app/` | Android TV app (Jetpack Compose UI, Media3 player) |

Min SDK 22 (Fire OS 5 and newer, all Android TV versions).

## Notes

- Only use this with a service you are licensed to access. The app ships with no channels or providers.
- Cleartext HTTP is allowed because most Xtream servers don't use TLS.
