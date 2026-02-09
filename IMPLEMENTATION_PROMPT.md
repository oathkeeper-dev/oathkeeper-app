# Oathkeeper Implementation Prompt

## Project Status

**Current Phase:** Phase 2 - ML Core Integration (Weeks 3-4)
**Last Updated:** February 9, 2026
**Previous Phase:** Phase 1 ✅ COMPLETE

---

## ⚠️ MAJOR ARCHITECTURAL CHANGE (Feb 9, 2026)

**Decision**: Switched from MediaProjection to AccessibilityService for screen capture.

**Rationale**:
- MediaProjection requires continuous "screen sharing" notification (poor UX)
- AccessibilityService provides event-driven capture without screen sharing dialog
- Covenant Eyes and similar apps likely use this approach
- Better battery efficiency and user experience

**Impact on Phase 2**:
- Replace ScreenCaptureService with OathkeeperAccessibilityService
- Different permission model (Accessibility Service vs MediaProjection)
- Use `takeScreenshot()` API (requires API 30+)
- Event-driven capture instead of continuous frame capture

---

## Pre-Implementation Setup

**IMPORTANT**: Read these documentation files first to understand the full architecture:
- `README.md` - Project overview and features
- `ARCHITECTURE.md` - Updated with AccessibilityService approach
- `ROADMAP.md` - Implementation phases and timeline
- `PROGRESS.md` - Current implementation status
- `docs/TECHNICAL_SPEC.md` - Detailed technical implementation (UPDATED with AccessibilityService)
- `docs/ML_MODELS.md` - Machine learning pipeline details

---

## Phase 1 Completion Summary ✅

Phase 1 (Foundation) has been successfully completed. The following components are in place:

### Implemented Components

**Project Structure:**
```
app/src/main/java/com/oathkeeper/app/
├── OathkeeperApplication.kt          # Application class
├── model/
│   ├── DetectionEvent.kt             # Detection data class
│   ├── PermissionItem.kt             # Permission model
│   └── TamperEvent.kt                # Tamper event model
├── receiver/
│   └── BootReceiver.kt               # Auto-start on boot
├── service/
│   └── ScreenCaptureService.kt       # Foreground service with notification
├── ui/
│   ├── MainActivity.kt               # Permission flow & welcome dialog
│   └── SettingsActivity.kt           # Configuration UI
└── util/
    ├── Constants.kt                  # App constants
    ├── PermissionUtils.kt            # Permission helpers
    └── PreferenceManager.kt          # Settings persistence
```

**Build Configuration:**
- Gradle 8.2 configured
- TensorFlow Lite 2.14.0 dependency ready
- SQLCipher 4.5.4 for future encryption
- Material Design Components
- Kotlin Coroutines 1.7.3

**Permissions (All 7 implemented):**
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_MEDIA_PROJECTION
- SYSTEM_ALERT_WINDOW
- PACKAGE_USAGE_STATS
- RECEIVE_BOOT_COMPLETED
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- WRITE_EXTERNAL_STORAGE (maxSdkVersion 28)

**Features Working:**
- ✅ App launches without crashes
- ✅ All permissions requested with explanations
- ✅ Welcome dialog on first run
- ✅ Foreground service with persistent notification
- ✅ Service auto-starts on device boot
- ✅ Settings screen (capture interval, thresholds)
- ✅ Clean UI/service separation for ML integration

---

## Current Phase: Phase 2 - ML Core Integration

**Goal:** Integrate TensorFlow Lite and implement basic NSFW detection

**Duration:** Weeks 3-4

### Required Components

#### 1. Model Conversion (Week 3)

**A. Download and Convert NSFW Model:**
- Download GantMan/nsfw_model (MobileNet V2)
- Convert to TensorFlow Lite format using Python
- Quantize model for mobile (INT8) to reduce size
- Expected model size: ~4MB after quantization
- Add to `app/src/main/assets/nsfw_mobilenet_v2.tflite`

**Conversion Script (Python):**
```python
import tensorflow as tf
import numpy as np

# Load model
model = tf.keras.models.load_model('nsfw_mobilenet_v2_224x224.h5')

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.int8]

# Representative dataset for quantization
def representative_dataset():
    for _ in range(100):
        data = np.random.rand(1, 224, 224, 3).astype(np.float32)
        yield [data]

converter.representative_dataset = representative_dataset
tflite_model = converter.convert()

# Save
with open('app/src/main/assets/nsfw_mobilenet_v2.tflite', 'wb') as f:
    f.write(tflite_model)
```

**B. Model Verification:**
- Test accuracy after conversion
- Compare with original model
- Document any accuracy loss

#### 2. TensorFlow Lite Integration (Week 4)

**A. Create NsfwClassifier Class:**
```kotlin
// Location: app/src/main/java/com/oathkeeper/app/ml/NsfwClassifier.kt
class NsfwClassifier(private val assetManager: AssetManager) {
    private var interpreter: Interpreter? = null
    private val inputSize = 224
    private val classes = listOf("drawings", "hentai", "neutral", "porn", "sexy")
    
    init { loadModel() }
    
    fun classify(bitmap: Bitmap): ClassificationResult
    private fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>>
}

data class ClassificationResult(
    val detectedClass: String,
    val confidence: Float,
    val severity: String
)
```

**Requirements:**
- Load TFLite model from assets
- Resize input to 224x224
- Normalize pixel values (0-1)
- Run inference on background thread
- Return class with highest confidence
- Use NNAPI for acceleration when available

**B. Create OathkeeperAccessibilityService:**

**NEW APPROACH**: Replace ScreenCaptureService with AccessibilityService

**Create new file**: `app/src/main/java/com/oathkeeper/app/service/OathkeeperAccessibilityService.kt`

**Requirements:**
1. Extend `AccessibilityService` base class
2. Initialize `NsfwClassifier` in `onServiceConnected()`
3. Monitor window state changes and app switches
4. Capture screenshots using `takeScreenshot()` (API 30+)
5. Run classification on captured screenshots
6. Log detections that meet thresholds

**Key differences from MediaProjection approach:**
- No ImageReader / VirtualDisplay setup
- No continuous frame capture
- Event-driven (on app switch, window change, timer)
- Uses `takeScreenshot()` callback API
- No "screen sharing" notification

**Implementation outline:**
```kotlin
class OathkeeperAccessibilityService : AccessibilityService() {
private lateinit var nsfwClassifier: NsfwClassifier

override fun onServiceConnected() {
super.onServiceConnected()
nsfwClassifier = NsfwClassifier(assets)
// Configure service to listen for window changes
}

override fun onAccessibilityEvent(event: AccessibilityEvent) {
when (event.eventType) {
AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
// App switched - check if relevant
if (isRelevantApp(event.packageName?.toString())) {
captureAndAnalyze()
}
}
}
}

private fun captureAndAnalyze() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
takeScreenshot(Display.DEFAULT_DISPLAY, executor, 
object : TakeScreenshotCallback {
override fun onSuccess(screenshot: ScreenshotResult) {
val bitmap = Bitmap.wrapHardwareBuffer(
screenshot.hardwareBuffer, 
screenshot.colorSpace
)
bitmap?.let { processScreenshot(it) }
}
override fun onFailure(errorCode: Int) {
Log.e(TAG, "Screenshot failed: $errorCode")
}
})
}
}

private fun processScreenshot(bitmap: Bitmap) {
CoroutineScope(Dispatchers.Default).launch {
val classification = nsfwClassifier.classify(bitmap)
if (shouldTriggerCapture(classification)) {
// Log detection, save screenshot, etc.
}
bitmap.recycle()
}
}
}
```

**C. Create Accessibility Service Configuration:**

**Create**: `app/src/main/res/xml/accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
android:description="@string/accessibility_service_description"
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
android:accessibilityFlags="flagDefault|flagReportViewIds"
android:canRetrieveWindowContent="true"
android:canTakeScreenshot="true"
android:notificationTimeout="100" />
```

**D. Update AndroidManifest.xml:**

Add AccessibilityService declaration:
```xml
<service android:name=".service.OathkeeperAccessibilityService"
android:enabled="true"
android:exported="true"
android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
<intent-filter>
<action android:name="android.accessibilityservice.AccessibilityService" />
</intent-filter>
<meta-data
android:name="android.accessibilityservice"
android:resource="@xml/accessibility_service_config" />
</service>
```

**Note**: Disable or remove the old ScreenCaptureService (MediaProjection-based)

**C. Create Database Layer:**

**DatabaseManager:**
```kotlin
// Location: app/src/main/java/com/oathkeeper/app/storage/DatabaseManager.kt
object DatabaseManager {
    fun initialize(context: Context)
    fun insertEvent(event: DetectionEvent): Long
    fun getAllEvents(): List<DetectionEvent>
    fun getEventsByDateRange(start: Long, end: Long): List<DetectionEvent>
    fun close()
}
```

**SQLCipher Database Schema:**
```sql
CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    detected_class TEXT NOT NULL,
    severity TEXT NOT NULL,
    confidence REAL NOT NULL,
    screenshot_path TEXT,
    app_name TEXT,
    is_reviewed BOOLEAN DEFAULT 0,
    notes TEXT,
    created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_events_severity ON events(severity);
```

**D. Detection Logging:**

When NSFW content detected (confidence > threshold):
1. Create `DetectionEvent` object
2. Insert into database
3. Show notification if enabled (prefs.enableNotifications)
4. Optionally capture unprocessed screenshot (Phase 3 will add pixelation)

**Thresholds:**
- `porn` class: confidence > 0.7 (critical)
- `sexy` class: confidence > 0.8 (warning)
- `drawings`/`hentai`: logged as info

#### 3. Frame Processing Pipeline

**A. ImageReader Setup:**
```kotlin
// In ScreenCaptureService.startCapture()
val metrics = resources.displayMetrics
imageReader = ImageReader.newInstance(
    metrics.widthPixels, 
    metrics.heightPixels, 
    PixelFormat.RGBA_8888, 
    2
)
```

**B. Frame Preprocessing:**
```kotlin
fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
    val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
    val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
    
    for (y in 0 until 224) {
        for (x in 0 until 224) {
            val pixel = resized.getPixel(x, y)
            input[0][y][x][0] = (pixel shr 16 and 0xFF) / 255.0f
            input[0][y][x][1] = (pixel shr 8 and 0xFF) / 255.0f
            input[0][y][x][2] = (pixel and 0xFF) / 255.0f
        }
    }
    
    resized.recycle()
    return input
}
```

### Phase 1.5: Architecture Migration

**NEW**: Before Phase 2 ML work, migrate from MediaProjection to AccessibilityService:

1. **Create OathkeeperAccessibilityService**
- Extend AccessibilityService base class
- Implement onServiceConnected()
- Configure accessibility service XML
- Add to AndroidManifest.xml

2. **Update MainActivity permission flow**
- Remove MediaProjection permission requests
- Add AccessibilityService enablement flow
- Guide user to system settings to enable service
- Handle service connection status

3. **Update PermissionUtils**
- Add checkAccessibilityServiceEnabled()
- Add requestAccessibilityService()
- Remove MediaProjection-related code

4. **Test AccessibilityService**
- Verify service starts correctly
- Test screenshot capture on Android 11+
- Confirm no "screen sharing" notification

---

### Phase 2 Implementation Order

5. **Download and convert NSFW model**
- Create `tools/convert_model.py`
- Run conversion script
- Verify model accuracy
- Add to assets

6. **Create NsfwClassifier class**
- Implement model loading
- Add preprocessing
- Implement inference method
- Test classification

7. **Set up SQLCipher database**
- Create DatabaseManager
- Implement schema
- Add CRUD operations
- Test database operations

8. **Update OathkeeperAccessibilityService**
- Initialize classifier in onServiceConnected()
- Implement captureAndAnalyze()
- Add inference loop
- Handle threading

9. **Implement detection logging**
- Create DetectionEvent objects
- Insert into database
- Add notifications
- Test end-to-end flow

10. **Add Events Viewer UI**
- Create EventsActivity
- Display detection list
- Show event details
- Add filtering

### Key Requirements

- All inference must run on background thread
- Model must load only once (in onCreate)
- Handle frame capture errors gracefully
- Database operations on background thread
- Show progress when loading model
- Implement memory management (recycle bitmaps)
- Add comprehensive error handling
- Log all detections with timestamps

### What's NOT Included in This Phase

DO NOT implement yet:
- YOLO person detection (Phase 3)
- Smart pixelation (Phase 3)
- Screenshot encryption (Phase 3)
- Tamper detection service (Phase 4)
- Report viewer (Phase 5)
- Biometric authentication (Phase 6)
- WebView reports (Phase 5)
- PDF export (Phase 5)

### Deliverables

Working app that:
1. Loads TensorFlow Lite NSFW model from assets
2. Captures screen frames every 2 seconds
3. Classifies frames using MobileNet V2
4. Logs detections to encrypted database
5. Shows notifications when content detected
6. Displays list of detection events
7. Uses user-configurable thresholds
8. Runs inference on background thread
9. No ANRs or memory leaks
10. Follows Android best practices

### Testing Checklist

- [ ] Model loads successfully from assets
- [ ] Classification runs without crashes
- [ ] Inference time < 100ms per frame
- [ ] Database creates and stores events
- [ ] Detections logged with correct timestamps
- [ ] Notifications appear when enabled
- [ ] Thresholds configurable in settings
- [ ] Memory usage remains stable
- [ ] No ANRs during inference
- [ ] Service continues running after detection
- [ ] Database persists across app restarts
- [ ] Classification accuracy acceptable

### Performance Targets

- **Inference Time:** < 100ms per frame
- **Memory Usage:** < 50MB for model + inference
- **Battery Impact:** < 2% additional drain per day
- **Database Size:** < 10MB for 1000 events

### Notes for Implementer

- Test model loading time on first run
- Consider caching model in memory
- Use coroutines for async operations
- Implement proper exception handling
- Add debug logging for troubleshooting
- Test with various NSFW content types
- Verify no memory leaks with LeakCanary
- Consider model warm-up on service start
- Test on devices with/without NNAPI support

### Current Code Structure

The existing `ScreenCaptureService` has a capture loop ready:
```kotlin
captureRunnable = object : Runnable {
    override fun run() {
        if (!isRunning) return
        // TODO: Phase 2 - Implement frame capture for ML inference
        Log.d(TAG, "Capture tick - Service is running")
        handler.postDelayed(this, prefs.captureInterval)
    }
}
```

Replace the TODO with actual frame capture and inference.

### Next Phase Preview

Phase 3 will add:
- YOLOv8-nano person detection
- Smart pixelation of person regions
- Screenshot capture on detection
- WebP compression
- AES-256-GCM encryption

Ensure Phase 2 detection is solid before proceeding.

---

## Quick Reference

**Key Files to Modify:**
1. `app/src/main/java/com/oathkeeper/app/service/ScreenCaptureService.kt`
2. Create: `app/src/main/java/com/oathkeeper/app/ml/NsfwClassifier.kt`
3. Create: `app/src/main/java/com/oathkeeper/app/storage/DatabaseManager.kt`
4. Create: `tools/convert_model.py`

**Model Path:**
- `app/src/main/assets/nsfw_mobilenet_v2.tflite`

**Database:**
- Location: App private directory
- Encryption: SQLCipher with AES-256
- Schema: See TECHNICAL_SPEC.md

**Dependencies Already Added:**
- TensorFlow Lite 2.14.0
- SQLCipher 4.5.4
- Kotlin Coroutines 1.7.3
