# Turbo64

An N64 emulator for Android. You play game files you own; the app contains no games, no BIOS and no download links.

- Package: `com.turbo64.emulator`
- Licence: GPL-3.0-or-later (see `LICENSE`)
- Publisher: AppKraft Studio

## What it does

- Opens `.z64`, `.n64`, `.v64`, `.zip` and `.7z` files from folders or files you pick (no storage permission).
- Touch controls: floating analog stick, direction pad, A, B, Z, L, R, Start and the four C buttons, with true multi-touch and a drag-to-move layout editor.
- Bluetooth and USB gamepads, with a button set-up wizard and a live test screen.
- Landscape and portrait play.
- In-game saves written every 30 seconds, a resume point every minute and on every exit, 10 save slots with screenshots, quick save and load with undo.
- Save backup and restore as one `.zip`, and an optional automatic backup to a folder you choose.
- Fast-forward, picture shape and quality options, GameShark cheats.
- No account, no analytics. Works offline.

## Built on

| Component | Licence |
|---|---|
| [Mupen64Plus-Next](https://github.com/libretro/mupen64plus-libretro-nx) (libretro core, GLES3 build, with GLideN64) | GPL-2.0-or-later |
| [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) 0.13.2 | GPL-3.0-or-later |
| AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, Apache Commons Compress | Apache-2.0 |
| XZ for Java | 0BSD |
| Outfit font | SIL OFL 1.1 |

The core binaries in `app/src/main/jniLibs/` are the unmodified builds from <https://buildbot.libretro.com> (nightly of 15 November 2026).

## Build

Requirements: JDK 17 or newer (JDK 20 is used here), Android SDK with platform 36.

```
./gradlew :app:assembleDebug
```

Checks used before every release:

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintRelease :app:minifyReleaseWithR8
```

Release signing uses a private upload key that is not part of this repository.

## Legal

No console BIOS, firmware or game is included. Play only games you legally own.
"Nintendo 64" is a trademark of Nintendo. Turbo64 is not affiliated with or endorsed by Nintendo.
