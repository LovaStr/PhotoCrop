# PhotoCrop

An Android app for cropping images and adjusting their colors.

Built with Kotlin and Jetpack Compose (Material 3). It uses only AndroidX/Jetpack libraries, with no third-party dependencies.

## Features

- Pick a photo with the system Photo Picker (no storage permission needed)
- Three tabs:
  - **Crop** – draggable rectangle (corner handles resize, drag inside to move); the **Crop** button applies it
  - **Adjust** – brightness, contrast and saturation sliders
  - **Curves** – RGB tone curves. Pick All or any of R/G/B, tap to add a point, drag to shape, double-tap a point to remove it
- Live preview while dragging; hold **Hold: original** to see the image without color edits
- **Cancel** discards the photo and all edits; **Save** writes a JPEG to `Pictures/PhotoCrop`

## Building

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run the `app` configuration on a device or emulator (Android 9 / API 28+).

## Structure

- `edit/` – crop and color models, image decode/render/save
- `ui/` – `EditorScreen`, `CropView`, `EditorViewModel`
