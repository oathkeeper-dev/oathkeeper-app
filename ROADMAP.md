# Oathkeeper Development Roadmap

## Overview

This document outlines the phased implementation plan for Oathkeeper. Total estimated duration: **10 weeks** to MVP (Minimum Viable Product).

## Phase 1: Foundation (Weeks 1-2)

**Goal**: Project setup, core permissions, basic infrastructure

### Week 1: Project Setup
- [x] Create Android project structure (Kotlin)
- [x] Configure build.gradle with dependencies:
  - TensorFlow Lite
  - SQLCipher
  - WebView
  - PDF generation library
- [x] Set up Git repository with .gitignore
- [x] Create basic package structure:
```
com.oathkeeper.app/
├── service/
├── ml/
├── storage/
├── ui/
├── receiver/
└── util/
```
- [x] Add basic logging framework

### Week 2: Permissions & Services
- [x] Implement permission request flow:
  - MediaProjection (screen capture)
  - SYSTEM_ALERT_WINDOW (overlay)
  - PACKAGE_USAGE_STATS (app monitoring)
  - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- [x] Create ForegroundService base class
- [x] Implement persistent notification
- [x] Create BootReceiver for auto-start
- [x] Add SettingsActivity with configuration options

**Deliverables**:
- App launches and requests all necessary permissions
- Foreground service runs with persistent notification
- Service auto-starts on boot

**Blockers to Watch**:
- MediaProjection permission UX on Android 10+
- Battery optimization whitelisting user flow

---

## Phase 2: ML Core (Weeks 3-4)

**Goal**: Integrate TensorFlow Lite and basic NSFW detection

### Week 3: Model Conversion
- [ ] Download GantMan/nsfw_model (MobileNet V2)
- [ ] Convert to TensorFlow Lite format:
  ```python
  import tensorflow as tf
  model = tf.keras.models.load_model('nsfw_mobilenet.h5')
  converter = tf.lite.TFLiteConverter.from_keras_model(model)
  converter.optimizations = [tf.lite.Optimize.DEFAULT]
  tflite_model = converter.convert()
  ```
- [ ] Quantize model for mobile (INT8)
- [ ] Test model accuracy after conversion
- [ ] Add model to Android assets

### Week 4: Integration
- [ ] Create TFLite interpreter wrapper class
- [ ] Implement frame preprocessing (resize to 224x224, normalize)
- [ ] Create inference pipeline with confidence thresholds
- [ ] Add detection logging to database
- [ ] Implement basic screenshot capture (unprocessed)
- [ ] Create DetectionEvent data class

**Deliverables**:
- App detects NSFW content and logs events
- Basic screenshots captured on detection
- Model runs on-device with acceptable latency

**Blockers to Watch**:
- Model conversion accuracy loss
- TFLite GPU delegate compatibility
- Memory leaks in interpreter

---

## Phase 3: Smart Pixelation (Weeks 5-6)

**Goal**: Add person detection and smart pixelation

### Week 5: YOLO Integration
- [ ] Download/convert YOLOv8-nano to TFLite
- [ ] Create person detection wrapper
- [ ] Integrate two-stage pipeline:
  1. NSFW classification triggers
  2. Person detection runs on original frame
- [ ] Extract bounding boxes from YOLO output
- [ ] Optimize inference for mobile (batch processing)

### Week 6: Pixelation & Storage
- [ ] Implement pixelation algorithm:
  ```kotlin
  fun applyPixelation(bitmap: Bitmap, boxes: List<BoundingBox>): Bitmap
  ```
- [ ] Add mosaic effect with configurable kernel size
- [ ] Convert to WebP format
- [ ] Implement AES-256-GCM encryption
- [ ] Create encrypted file storage manager
- [ ] Delete raw screenshots after processing

**Deliverables**:
- Smart pixelation working (person regions only)
- Encrypted screenshot storage
- Original unprocessed images deleted

**Blockers to Watch**:
- YOLO bounding box accuracy
- Pixelation performance (use RenderScript or GPU?)
- Encryption/decryption speed

---

## Phase 4: Tamper Detection (Week 7)

**Goal**: Implement tamper detection and warning system

### Tasks
- [ ] Create TamperDetectionService
- [ ] Implement health check alarm (every 30s):
  ```kotlin
  fun checkServiceHealth(): HealthStatus
  ```
- [ ] Monitor MediaProjection state
- [ ] Detect permission changes
- [ ] Create persistent warning overlay:
  - Shows when tampering detected
  - Cannot be dismissed without fixing issue
  - Logs to tamper_events table
- [ ] Implement auto-restart logic
- [ ] Add tamper event viewer in UI

**Deliverables**:
- Tamper events detected and logged
- Warning overlay displays on issues
- Auto-recovery attempts service restart

**Blockers to Watch**:
- False positives in tamper detection
- Overlay permission persistence
- Battery impact of frequent health checks

---

## Phase 5: Reports UI (Weeks 8-9)

**Goal**: Interactive report viewer with export

### Week 8: WebView Interface
- [ ] Create local HTML/CSS/JS assets
- [ ] Implement WebView with JavaScript bridge
- [ ] Build interactive timeline view
- [ ] Add filtering and search functionality
- [ ] Create statistics dashboard (Chart.js)
- [ ] Implement evidence viewer with metadata display

### Week 9: Export Functionality
- [ ] Create PDF export (iText or similar):
  - Statistics summary
  - Embedded pixelated images
  - Timeline view
- [ ] Implement JSON export
- [ ] Add encrypted backup/restore:
  - Export database + screenshots as encrypted archive
  - Import from backup file
- [ ] Create bulk operations (delete, export selected)

**Deliverables**:
- Interactive report viewer functional
- PDF and JSON export working
- Backup/restore implemented

**Blockers to Watch**:
- WebView performance with many images
- PDF generation memory usage
- Large backup file handling

---

## Phase 6: Security & Polish (Week 10)

**Goal**: Encryption, optimization, and setup wizard

### Tasks
- [ ] Implement SQLCipher database encryption
- [ ] Add biometric authentication for app access
- [ ] Create setup wizard:
  - Welcome and explanation
  - Permission requests with context
  - Partner contact setup (optional)
  - Configuration recommendations
- [ ] Optimize battery usage:
  - Adaptive capture rate (faster in browsers)
  - Reduce inference frequency when idle
  - Batch database writes
- [ ] Add comprehensive error handling
- [ ] Create user documentation in app
- [ ] Final testing on multiple Android versions (10-14)

**Deliverables**:
- All data encrypted at rest
- Setup wizard guides new users
- Battery optimized
- Production-ready build

**Blockers to Watch**:
- Biometric API compatibility across devices
- Encryption key management
- Performance on low-end devices

---

## Post-MVP Enhancements (Future)

### Phase 7: Polish & Feedback (Weeks 11-12)
- [ ] Beta testing with users
- [ ] Address false positive issues
- [ ] Optimize ML model thresholds
- [ ] Add user feedback mechanism
- [ ] Improve UI/UX based on feedback

### Phase 8: Advanced Features (Weeks 13-14)
- [ ] Category-specific pixelation settings
- [ ] Custom detection categories
- [ ] Scheduled reports (daily/weekly summaries)
- [ ] Widget for quick status check
- [ ] Dark mode support

### Phase 9: Distribution (Week 15)
- [ ] Create F-Droid metadata
- [ ] Submit to F-Droid repository
- [ ] Create project website
- [ ] Write user documentation
- [ ] Create video tutorial

---

## Milestones

| Milestone | Date | Criteria |
|-----------|------|----------|
| **M1: Foundation** | Week 2 | App runs as foreground service with all permissions |
| **M2: Detection** | Week 4 | NSFW detection working, basic screenshots captured |
| **M3: Privacy** | Week 6 | Smart pixelation and encryption implemented |
| **M4: Accountability** | Week 7 | Tamper detection and warnings functional |
| **M5: Insights** | Week 9 | Report viewer with export working |
| **M6: MVP** | Week 10 | Production-ready build with setup wizard |

---

## Resource Requirements

### Development
- Android Studio Arctic Fox or later
- Kotlin 1.7+
- Minimum SDK: API 29 (Android 10)
- Target SDK: API 34 (Android 14)

### Testing Devices
- Android 10 device (Samsung Galaxy S20 or similar)
- Android 12 device (Google Pixel 5 or similar)
- Android 14 device (Google Pixel 7 or similar)

### External Dependencies
- TensorFlow Lite 2.13+
- SQLCipher 4.5+
- YOLOv8 (Ultralytics) for model export
- Python 3.9+ for model conversion

---

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| MediaProjection permission issues on Android 10+ | High | High | Document user instructions, implement permission monitoring |
| Model accuracy degradation after TFLite conversion | Medium | High | Thorough testing, keep original model for comparison |
| Battery drain complaints | Medium | Medium | Implement adaptive capture rates, optimize inference |
| False positives causing user frustration | High | Medium | Configurable thresholds, user feedback mechanism |
| App store rejection (if attempting Play Store) | High | High | Focus on F-Droid, maintain sideload option |
| Device compatibility issues | Medium | Medium | Test on multiple devices, graceful degradation |

---

## Success Criteria

The MVP is considered successful when:

1. App captures and pixelates screenshots on NSFW detection
2. All data remains encrypted and local
3. Tamper detection logs all circumvention attempts
4. Report viewer provides useful insights
5. Setup wizard enables non-technical users to install
6. Battery impact remains under 5% daily drain
7. App runs reliably for 7+ days without crashes

---

## Notes for Future Developers

- See [docs/TECHNICAL_SPEC.md](docs/TECHNICAL_SPEC.md) for implementation details
- Model files are not included in repository due to size
- Use Python scripts in `tools/` directory for model conversion
- Test thoroughly on physical devices, not just emulators
- Respect user privacy at all times - this is the core principle
