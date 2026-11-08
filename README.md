# PhotoCrop

An Android app for cropping images and adjusting their colors.

Built with Kotlin and Jetpack Compose (Material 3). It uses only AndroidX/Jetpack libraries, with no third-party dependencies.

## Features

- Pick a photo with the system Photo Picker (no storage permission needed)
- Crop with a draggable rectangle (corner handles resize, drag inside to move)
- Adjust brightness, contrast and saturation with live preview
- Save the result as JPEG to `Pictures/PhotoCrop`

## Building

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run the `app` configuration on a device or emulator (Android 9 / API 28+).

## Structure

- `edit/` – crop and color models, image decode/render/save
- `ui/` – `EditorScreen`, `CropView`, `EditorViewModel`
