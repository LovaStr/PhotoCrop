# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

PhotoCrop is a single-module Android app (Kotlin + Jetpack Compose, Material 3, `minSdk 28`, package `com.lovastr.photocrop`) for cropping photos and adjusting their colors. It uses only AndroidX/Jetpack libraries; ask before adding any third-party dependency. Versions live in `gradle/libs.versions.toml`.

## Commands

Windows/PowerShell. Gradle needs a JDK; use the one bundled with Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug        # build; APK at app\build\outputs\apk\debug\app-debug.apk
```

The SDK is at `%LOCALAPPDATA%\Android\Sdk` (referenced by the gitignored `local.properties`). There are no tests and no lint setup yet.

Run on the emulator (AVD `Pixel_35`, Android 15, x86_64):

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd","Pixel_35","-no-snapshot-save"
& "$sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$sdk\platform-tools\adb.exe" shell am start -n com.lovastr.photocrop/.MainActivity
```

Emulator testing gotchas:
- Take screenshots to `/data/local/tmp` (`adb shell screencap -p /data/local/tmp/s.png`, then `adb pull`). Anything written to `/sdcard` gets indexed into the gallery, and then shows up in the app's photo picker, where picking it looks like a bug (e.g. screenshots of the crop overlay appear as "grid lines" baked into the photo).
- Test photos (`landscape.jpg`, `portrait.jpg`, `square.jpg`) live in the emulator's `/sdcard/Pictures`; saved output goes to `/sdcard/Pictures/PhotoCrop`.
- The emulator process can die between tool calls; check `adb devices` before assuming it is up.

## Architecture

State flows one way: `EditorViewModel` (in `ui/`) holds all editing state as Compose `mutableStateOf`; `EditorScreen` renders it; `edit/` holds the pure image logic with no UI.

**Two bitmaps, not one.** `EditorViewModel` keeps `source` (full resolution, max edge 4096) and `preview` (downscaled to 1024) plus `curved` (`preview` with the RGB curves applied). The UI only ever draws `preview`/`curved`; `source` is touched only on crop and save. Curve application is per-pixel, so it runs on a background coroutine (`refreshCurved`) and a newer edit cancels older work.

**Edit pipeline and its ordering.** Crop is destructive and explicit: the crop rectangle (`NormRect`, normalized 0..1) is only a selection until `applyCrop()` replaces `source` with the cropped bitmap. Color edits are non-destructive and applied in this fixed order, which the live preview and `renderEdited()` (used by Save) must both follow: **RGB curves (per-channel 256-entry LUTs) first, then the brightness/contrast/saturation `ColorMatrix`**. In the preview the curves are baked into `curved` and the matrix is applied at draw time via a Compose `ColorFilter`; at save time `renderEdited` does both on the full-res bitmap. Save does not apply an un-applied crop selection.

**Curves** (`edit/Curves.kt`): each channel is a sorted list of `CurvePoint`s with fixed endpoints at x=0 and x=1, interpolated with a monotone cubic (Fritsch–Carlson) into a LUT. `CurveEditor` edits all channels currently selected in the chips; touching near a point drags it, empty space adds one, double-tap removes it.

**Crop UI** (`CropView.kt`): the overlay is drawn on a `Canvas` inside a `Box` with a 24dp margin. The `Box` owns the pointer input so corner handles at the image edge stay fully touchable; touch coordinates are offset by that margin before hit-testing. The overlay is only shown (and gestures only active) on the Crop tab.

**Compare/cancel semantics:** the "Hold: original" button (pressed-state of an `OutlinedButton`) swaps the displayed bitmap to `preview` with no color filter. Cancel discards the photo and all edits and returns to the start screen (no confirmation).

**I/O** (`edit/ImageIO.kt`): decode uses `ImageDecoder` with the software allocator (so `getPixels` works and EXIF rotation is handled); the photo is picked with the system Photo Picker (no storage permission); saving inserts a JPEG via `MediaStore` into `Pictures/PhotoCrop`.
