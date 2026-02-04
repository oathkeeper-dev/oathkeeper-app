# Oathkeeper Implementation Starter Prompt

## Pre-Implementation Setup

**IMPORTANT**: Read these documentation files first to understand the full architecture:
- `README.md` - Project overview and features
- `ARCHITECTURE.md` - High-level system design and components
- `ROADMAP.md` - Implementation phases and timeline
- `docs/TECHNICAL_SPEC.md` - Detailed technical implementation
- `docs/ML_MODELS.md` - Machine learning pipeline details

## Current Phase

**Phase 1: Foundation (Weeks 1-2)**

Implement the basic infrastructure for the Oathkeeper Android app.

## Required Components

### 1. Android Project Structure
- Language: Kotlin
- Target SDK: 34
- Minimum SDK: 29 (Android 10+)
- Package structure:
  ```
  com.oathkeeper.app/
  ├── service/       # Foreground services
  ├── ui/            # Activities and Fragments
  ├── receiver/      # BroadcastReceivers
  ├── util/          # Utility classes
  └── model/         # Data classes
  ```

### 2. Dependencies (build.gradle)
```gradle
dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    
    // TensorFlow Lite (for future ML integration)
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    
    // Database encryption
    implementation 'net.zetetic:android-database-sqlcipher:4.5.4'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 3. AndroidManifest.xml Permissions
Required permissions:
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `SYSTEM_ALERT_WINDOW`
- `PACKAGE_USAGE_STATS`
- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28)
- `USE_BIOMETRIC` (optional)

### 4. MainActivity
- Request all required permissions on first launch
- Handle permission denials gracefully
- Provide clear explanations for each permission
- Launch setup wizard if first run

### 5. ScreenCaptureService
- Extend Service class
- Run as foreground service
- Display persistent notification
- Handle MediaProjection setup (permission request, not actual capture yet)
- Implement START_STICKY for auto-restart
- Create notification channel

### 6. BootReceiver
- Extend BroadcastReceiver
- Listen for BOOT_COMPLETED action
- Auto-start ScreenCaptureService on boot
- Handle service restart if terminated

### 7. SettingsActivity
- Basic configuration UI
- Capture interval setting (default 2000ms)
- Threshold settings for detection
- Enable/disable notifications
- View tamper events (basic list)

## Implementation Order

1. **Create project structure and dependencies**
2. **Set up AndroidManifest with all permissions**
3. **Implement MainActivity with permission flow**
4. **Create ScreenCaptureService (foreground service)**
5. **Implement BootReceiver**
6. **Create SettingsActivity**
7. **Test all permissions are requested properly**
8. **Verify service runs persistently**
9. **Test auto-restart on boot**

## Key Requirements

- All on-device processing (no network calls)
- Foreground service must show persistent notification
- Service should auto-restart if killed
- Handle Android 10+ MediaProjection permission properly
- Request battery optimization exemption
- Clean separation between UI and service layers

## What's NOT Included in This Phase

DO NOT implement yet:
- TensorFlow Lite ML models
- Actual screenshot capture
- Image processing or pixelation
- Database storage
- Report viewer
- ML inference pipeline
- Tamper detection logic (framework only)
- Encryption

## Deliverables

Working app that:
1. Requests all permissions on first launch
2. Shows persistent notification when service runs
3. Auto-starts on device boot
4. Has functional settings screen
5. Service framework is ready for ML integration
6. No crashes or ANRs
7. Follows Android best practices

## Testing Checklist

- [ ] App launches without crashes
- [ ] All permissions are requested
- [ ] Foreground notification displays correctly
- [ ] Service runs after granting permissions
- [ ] Service auto-starts on reboot
- [ ] Settings screen opens and saves preferences
- [ ] App handles permission denials gracefully
- [ ] No memory leaks in service

## Notes for Implementer

- Keep code modular for future ML integration
- Use dependency injection if possible (Hilt/Koin)
- Follow MVVM or MVI architecture patterns
- Add comprehensive logging for debugging
- Test on physical devices, not just emulator
- Consider Android 10+ permission quirks for MediaProjection
- Service must handle configuration changes (rotation)

## Next Phase Preview

Phase 2 will integrate:
- TensorFlow Lite models
- NSFW classification
- Basic detection logging
- ML inference pipeline

Ensure Phase 1 foundation is solid before proceeding.
