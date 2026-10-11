# Dynamic Island for Android

A lightweight, native Kotlin Android app inspired by Apple's Dynamic Island. The project will provide an animated top pill and expandable live cards for media playback, notifications, charging, and timers.

## Design goals
- Smooth, compact black island with fluid expand/collapse animation
- Android overlay service, enabled only after the user grants overlay permission
- Notification and media-session integration through Android's public APIs
- Minimal dependencies and a small debug APK
- GitHub Actions workflow to build and publish a downloadable debug APK

## Build
Open this project in Android Studio with JDK 17 and Android SDK installed. CI builds are provided by GitHub Actions.

## Permissions
Overlay and notification access are optional and are requested only for the corresponding features. Notification content is processed locally.
