# Oathkeeper Architecture

## System Overview

Oathkeeper is a local accountability application for Android that monitors screen content using machine learning to detect NSFW (Not Safe For Work) material. The system is designed with privacy as a core principle - all processing happens on-device and no data leaves the device.

## High-Level Architecture

**ARCHITECTURE UPDATE (Feb 9, 2026):** Switched from MediaProjection to AccessibilityService for screen capture. This provides a more discreet user experience without continuous "screen sharing" notifications.

```
┌─────────────────────────────────────────────────────────────────┐
│ Android Device                                                  │
│                                                                 │
│  ┌─────────────────────────────────────────┐                    │
│  │ AccessibilityService                      │                    │
│  │ (Screen monitoring & capture)             │                    │
│  │                                           │                    │
│  │ • Monitors window content changes         │                    │
│  │ • Detects foreground app switches         │                    │
│  │ • Captures screenshots via API 30+      │                    │
│  │ • Triggers analysis on relevant events  │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                    │
│  │ Event-Driven Capture Logic              │                    │
│  │ - App switched to browser/social        │                    │
│  │ - URL accessed (via VPN optional)       │                    │
│  │ - Periodic check every 2-5 seconds      │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                    │
│  │ ML Inference Pipeline                   │                    │
│  │ ┌─────────────┐ ┌─────────────┐        │                    │
│  │ │ NSFW        │ │ Person      │        │                    │
│  │ │ Classifier  │───▶│ Detector    │        │                    │
│  │ │ (4MB)       │ │ (3.5MB)     │        │                    │
│  │ └─────────────┘ └─────────────┘        │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                    │
│  │ Screenshot Processing                   │                    │
│  │ - Apply pixelation to bounding boxes    │                    │
│  │ - Compress to WebP format                 │                    │
│  │ - Encrypt with AES-256                  │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                    │
│  │ Local Storage Layer                     │                    │
│  │ ┌──────────────┐ ┌────────────────┐    │                    │
│  │ │ SQLCipher    │ │ Encrypted      │    │                    │
│  │ │ Database     │ │ File Storage   │    │                    │
│  │ │ (Events)     │ │ (Screenshots)  │    │                    │
│  │ └──────────────┘ └────────────────┘    │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                    │
│  │ Report Viewer                           │                    │
│  │ (Local WebView with Interactive UI)     │                    │
│  └─────────────────────────────────────────┘                    │
│           │                                                     │
└─────────────────────────────────────────────────────────────────┘
```
┌─────────────────────────────────────────────────────────────────┐
│                        Android Device                           │
│                                                                 │
│  ┌─────────────────┐    ┌──────────────────┐                   │
│  │  MediaProjection │    │  Foreground       │                   │
│  │  Service         │───▶│  Service          │                   │
│  │                  │    │  (Notification)   │                   │
│  └─────────────────┘    └──────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                   │
│  │        ML Inference Pipeline            │                   │
│  │  ┌─────────────┐    ┌─────────────┐    │                   │
│  │  │ NSFW        │    │ Person      │    │                   │
│  │  │ Classifier  │───▶│ Detector    │    │                   │
│  │  │ (4MB)       │    │ (3.5MB)     │    │                   │
│  │  └─────────────┘    └─────────────┘    │                   │
│  └─────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                   │
│  │      Screenshot Processing              │                   │
│  │  - Apply pixelation to bounding boxes   │                   │
│  │  - Compress to WebP format              │                   │
│  │  - Encrypt with AES-256                 │                   │
│  └─────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                   │
│  │        Local Storage Layer              │                   │
│  │  ┌──────────────┐  ┌────────────────┐  │                   │
│  │  │ SQLCipher    │  │ Encrypted      │  │                   │
│  │  │ Database     │  │ File Storage   │  │                   │
│  │  │ (Events)     │  │ (Screenshots)  │  │                   │
│  │  └──────────────┘  └────────────────┘  │                   │
│  └─────────────────────────────────────────┘                   │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────────────────────────────┐                   │
│  │         Report Viewer                   │                   │
│  │  (Local WebView with Interactive UI)    │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Screen Monitoring Service (AccessibilityService)

**Purpose**: Monitor screen content and capture screenshots for analysis

**Implementation**:
- Android `AccessibilityService` API
- Extends `AccessibilityService` base class
- Event-driven capture (on window content changes, app switches)
- Periodic capture every 2-5 seconds when relevant apps are active
- Uses `takeScreenshot()` API (Android 11+ / API 30+)

**Permissions Required**:
- `BIND_ACCESSIBILITY_SERVICE` - Declared in manifest
- User must enable service in Accessibility settings
- No "screen sharing" dialog - more discreet UX

**Advantages over MediaProjection**:
- No persistent "screen sharing" notification
- No need for MediaProjection permission dialog
- Can detect app/window changes in real-time
- More power-efficient (event-driven vs continuous)

**Challenges**:
- Requires Accessibility Service permission (different UX)
- `takeScreenshot()` requires API 30+ (Android 11+)
- Screenshots may be at screen resolution (larger files)
- Some OEMs restrict background services

**Fallback for older devices**:
- MediaProjection for Android 10 and below
- Or reduced functionality mode

### 2. ML Inference Pipeline

**Two-Stage Approach**:

#### Stage 1: NSFW Classification
- **Model**: GantMan/nsfw_model MobileNet V2
- **Size**: ~4MB (after TFLite conversion with quantization)
- **Input**: 224x224 RGB image
- **Classes**: `drawings`, `hentai`, `neutral`, `porn`, `sexy`
- **Trigger**: `porn` > 0.7 OR `sexy` > 0.8 confidence
- **Inference Time**: ~50ms on modern devices

#### Stage 2: Person Detection
- **Model**: YOLOv8-nano
- **Size**: ~3.5MB
- **Input**: Original frame resolution
- **Output**: Bounding boxes for person regions
- **Purpose**: Provides coordinates for smart pixelation
- **Inference Time**: ~30ms

**Pipeline Flow**:
1. Capture frame at native resolution
2. Resize to 224x224 for classification
3. If classification triggers, run YOLO on original resolution
4. Pass bounding boxes to screenshot processor

### 3. Screenshot Processing

**Process**:
1. Capture full-resolution screenshot (only on positive detection)
2. Apply pixelation mask to bounding box regions
   - Kernel size proportional to box dimensions
   - Mosaic effect (square blocks)
   - Non-person regions remain visible for context
3. Convert to WebP format (lossy, quality 80)
4. Encrypt with AES-256-GCM
5. Delete raw unprocessed screenshot

**Pixelation Strategy**:
- Pixelate only detected person regions (Option A)
- Same pixelation strength regardless of severity category
- Preserves UI elements and text outside person regions

### 4. Local Storage Layer

#### Database (SQLCipher)
**Schema**:
```sql
-- Events Table
CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp DATETIME NOT NULL,
    detected_class TEXT NOT NULL,  -- 'porn', 'sexy', etc.
    severity TEXT NOT NULL,         -- 'critical', 'warning', 'info'
    confidence REAL NOT NULL,
    screenshot_path TEXT,
    is_reviewed BOOLEAN DEFAULT 0,
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tamper Events Table
CREATE TABLE tamper_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp DATETIME NOT NULL,
    event_type TEXT NOT NULL,       -- 'service_stopped', 'permission_revoked', etc.
    details TEXT,                   -- JSON with additional info
    recovered BOOLEAN DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Settings Table
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### File Storage
- **Location**: App private directory
- **Format**: WebP images
- **Encryption**: AES-256-GCM with device-specific key
- **Naming**: `{timestamp}_{hash}.webp`
- **Size**: ~100-300KB per image

### 5. Report Viewer

**Implementation**: Local WebView with HTML/JS/CSS assets

**Features**:
- Interactive timeline of events
- Filter by: date range, severity, detection class
- Search functionality
- Statistics dashboard (daily/weekly breakdowns)
- Evidence viewer with metadata
- Export to PDF or JSON
- Bulk operations (delete, export)

**Export Formats**:
- **PDF**: Embedded images with statistics summary
- **JSON**: Machine-readable event data
- **Encrypted Backup**: Full database + screenshots for restoration

### 6. Tamper Detection System

**Monitored Events**:
1. Service stopped (app force-killed)
2. MediaProjection permission revoked
3. Accessibility service disabled
4. Battery optimization re-enabled
5. App uninstalled (detected on reinstall)

**Response**:
- Log event to tamper_events table
- Display persistent warning overlay
- Attempt auto-recovery (restart service if possible)

**Implementation**:
- Health check alarm every 30 seconds
- Boot receiver for auto-restart
- Permission state monitoring
- Watchdog timer service

## Data Flow

```
User opens app/grants permissions
│
▼
Enable Accessibility Service in system settings
│
▼
AccessibilityService starts monitoring
│
▼
Event detected (app switch/content change/timer)
│
▼
Capture screenshot via takeScreenshot() API
│
▼
Resize to 224x224 ──▶ NSFW Classifier
│                     │
│                     ▼
│    Confidence < threshold ──▶ Discard screenshot
│                     │
│                     ▼
│    Confidence >= threshold
│                     │
▼                     ▼
Captured screenshot ───▶ Person Detector (YOLO)
│
▼
Get bounding boxes
│
▼
Apply pixelation to bounding boxes
│
▼
Convert to WebP and encrypt
│
▼
Save to encrypted storage
│
▼
Log event to database
│
▼
Show detection notification (optional)
```

**Key Difference**: Screenshots are captured on-demand via AccessibilityService rather than continuously from a VirtualDisplay. This reduces battery impact and eliminates the "screen sharing" notification.
User opens app/grants permissions
         │
         ▼
MediaProjection Service starts
         │
         ▼
Continuous frame capture (every 2-3s)
         │
         ▼
Resize to 224x224 ──▶ NSFW Classifier
         │                    │
         │                    ▼
         │            Confidence < threshold ──▶ Discard frame
         │                    │
         │                    ▼
         │            Confidence >= threshold
         │                    │
         ▼                    ▼
Original frame ───▶ Person Detector (YOLO)
                           │
                           ▼
                   Get bounding boxes
                           │
                           ▼
              Capture full-resolution screenshot
                           │
                           ▼
              Apply pixelation to bounding boxes
                           │
                           ▼
              Encrypt and save to storage
                           │
                           ▼
              Log event to database
                           │
                           ▼
              Show detection notification (optional)
```

## Severity Categories

All detections are categorized for logging purposes (pixelation remains uniform):

1. **Critical**: `porn` class with confidence > 0.7
   - Outright nudity, explicit sexual content

2. **Warning**: `sexy` class with confidence > 0.8
   - Revealing clothing, suggestive poses

3. **Info**: `drawings` or `hentai` classes
   - Animated/illustrated content, swimwear, underwear

## Security Considerations

### Encryption
- **Database**: SQLCipher with AES-256
- **Files**: AES-256-GCM with authentication
- **Keys**: Derived from device-specific entropy + user password (optional)

### Privacy
- No network permissions required
- All ML models run locally
- No analytics, crash reporting, or telemetry
- User can delete all data at any time

### Access Control
- App protected by Android biometrics/PIN (optional)
- Report viewer requires authentication
- Export operations require confirmation

## Performance Targets

- **Battery Impact**: <5% additional drain per day
- **Inference Latency**: <100ms per frame (both models)
- **Memory Usage**: <200MB RAM while running
- **Storage Growth**: <100MB per month (typical use)
- **UI Responsiveness**: <16ms for report viewer interactions

## Limitations

1. **DRM Content**: Cannot capture Netflix, Hulu, etc. (shows black screen)
2. **Permission Persistence**: Android 10+ requires periodic re-authorization
3. **False Positives**: May trigger on innocent content (medical, art, fitness)
4. **Battery**: Continuous monitoring impacts battery life
5. **Circumvention**: Determined users can uninstall app (detected but not prevented)

## Future Enhancements

- Custom model training for specific categories
- Adaptive pixelation based on severity
- Remote accountability partner notifications (opt-in)
- Cross-device synchronization (encrypted)
- AI-powered false positive filtering

## References

- [Technical Specification](docs/TECHNICAL_SPEC.md)
- [ML Models Documentation](docs/ML_MODELS.md)
- [TensorFlow Lite Android Guide](https://www.tensorflow.org/lite/android)
- [MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection)
