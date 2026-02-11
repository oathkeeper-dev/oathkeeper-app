# Technical Specification

## Implementation Status

### Phase 1: Foundation - COMPLETED ✅

**Date Completed:** February 4, 2026

**Status:** All Phase 1 components implemented and tested.

**Completed Components:**

- [x] Android project structure with Kotlin (minSdk 29, targetSdk 34)
- [x] Build.gradle configured with all required dependencies:
  - TensorFlow Lite 2.14.0
  - SQLCipher 4.5.4
  - Material Design Components
  - Kotlin Coroutines
  - Lifecycle components
- [x] AndroidManifest.xml with all 7 required permissions
- [x] Package structure: com.oathkeeper.app/{service,ui,receiver,util,model}

**Activities & Services:**
- [x] MainActivity - Permission handling flow with welcome dialog
- [x] SettingsActivity - Configuration UI for thresholds and intervals
- [x] ScreenCaptureService - Foreground service with persistent notification
- [x] BootReceiver - Auto-start service on device boot
- [x] OathkeeperApplication - Application class

**Utility Classes:**
- [x] Constants - All configuration constants
- [x] PreferenceManager - SharedPreferences wrapper
- [x] PermissionUtils - Permission checking/requesting helpers
- [x] ModelUtils - Validates ML model file existence on startup

**Data Models:**
- [x] DetectionEvent - Detection event data class
- [x] TamperEvent - Tamper event data class
- [x] PermissionItem - Permission item model

**UI Resources:**
- [x] activity_main.xml - Main interface layout
- [x] activity_settings.xml - Settings interface layout
- [x] Light and dark theme support
- [x] All string resources

**Next Phase:** Phase 2 - ML Core Integration (TensorFlow Lite NSFW detection)

---

## Architecture Update (Feb 9, 2026)

**MAJOR CHANGE**: Switched from MediaProjection to AccessibilityService for screen capture. This provides:
- No "screen sharing" notification/permission dialog
- Event-driven capture (more efficient)
- Better user experience (more discreet)

---

## Android Manifest Permissions

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

<!-- Accessibility Service (REQUIRED - replaces MediaProjection) -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- Foreground service for continuous operation -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Overlay for tamper warnings -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- App monitoring (for app usage stats) -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />

<!-- Auto-restart -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Prevent battery optimization -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Storage -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
android:maxSdkVersion="28" />

<!-- Biometric auth (optional) -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

<application
android:name=".OathkeeperApplication"
android:allowBackup="false"
android:icon="@mipmap/ic_launcher"
android:label="@string/app_name"
android:roundIcon="@mipmap/ic_launcher_round"
android:supportsRtl="true"
android:theme="@style/Theme.Oathkeeper">

<!-- Main Activity -->
<activity android:name=".MainActivity"
android:exported="true"
android:launchMode="singleTask">
<intent-filter>
<action android:name="android.intent.action.MAIN" />
<category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
</activity>

<!-- Accessibility Service (NEW - replaces ScreenCaptureService) -->
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

<!-- Legacy Screen Capture Service (deprecated, kept for reference) -->
<service android:name=".service.ScreenCaptureService"
android:enabled="false"
android:exported="false"
                 android:foregroundServiceType="mediaProjection" />
        
        <!-- Tamper Detection Service -->
        <service android:name=".service.TamperDetectionService"
                 android:enabled="true"
                 android:exported="false" />
        
        <!-- Boot Receiver -->
        <receiver android:name=".receiver.BootReceiver"
                  android:enabled="true"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
        
    </application>
</manifest>
```

## Key Classes

### OathkeeperAccessibilityService.kt (NEW)

**Replaces MediaProjection-based ScreenCaptureService**

```kotlin
class OathkeeperAccessibilityService : AccessibilityService() {

private lateinit var nsfwClassifier: NsfwClassifier
private lateinit var personDetector: PersonDetector
private val handler = Handler(Looper.getMainLooper())
private var lastCaptureTime = 0L
private val captureIntervalMs = 2000L // 2 seconds

companion object {
private const val TAG = "OathkeeperAccessibility"
private val RELEVANT_PACKAGES = setOf(
"com.android.chrome",
"org.mozilla.firefox",
"com.opera.browser",
"com.facebook.katana",
"com.instagram.android",
"com.twitter.android",
"com.reddit.frontpage",
// Add more as needed
)
}

override fun onServiceConnected() {
super.onServiceConnected()
Log.d(TAG, "Accessibility service connected")

// Configure service
serviceInfo = serviceInfo.apply {
eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
AccessibilityEvent.TYPE_VIEW_CLICKED
feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
notificationTimeout = 100
}

// Initialize ML models
nsfwClassifier = NsfwClassifier(assets)
personDetector = PersonDetector(assets)

// Start periodic capture timer
startPeriodicCapture()
}

override fun onAccessibilityEvent(event: AccessibilityEvent) {
when (event.eventType) {
AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
// App switched - check if it's a relevant app
val packageName = event.packageName?.toString()
if (packageName != null && isRelevantApp(packageName)) {
Log.d(TAG, "Switched to relevant app: $packageName")
captureAndAnalyze()
}
}
AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
// Content changed - might need new screenshot
if (shouldCapture()) {
captureAndAnalyze()
}
}
}
}

override fun onInterrupt() {
Log.d(TAG, "Accessibility service interrupted")
}

private fun isRelevantApp(packageName: String): Boolean {
return RELEVANT_PACKAGES.contains(packageName)
}

private fun shouldCapture(): Boolean {
val currentTime = System.currentTimeMillis()
return (currentTime - lastCaptureTime) >= captureIntervalMs
}

private fun startPeriodicCapture() {
handler.postDelayed(object : Runnable {
override fun run() {
if (isRelevantAppInForeground()) {
captureAndAnalyze()
}
handler.postDelayed(this, captureIntervalMs)
}
}, captureIntervalMs)
}

private fun isRelevantAppInForeground(): Boolean {
// Check current foreground app using UsageStatsManager
val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val time = System.currentTimeMillis()
val stats = usageStatsManager.queryUsageStats(
UsageStatsManager.INTERVAL_DAILY,
time - 1000 * 10, // Last 10 seconds
time
)

val recentApp = stats?.maxByOrNull { it.lastTimeUsed }
return recentApp != null && isRelevantApp(recentApp.packageName)
}

private fun captureAndAnalyze() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
override fun onSuccess(screenshot: ScreenshotResult) {
val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
bitmap?.let {
lastCaptureTime = System.currentTimeMillis()
processScreenshot(it)
}
}

override fun onFailure(errorCode: Int) {
Log.e(TAG, "Screenshot failed with error: $errorCode")
}
})
} else {
// Fallback for Android 10 and below - use MediaProjection
// or skip screenshot capture
Log.w(TAG, "Screenshot requires Android 11+")
}
}

private fun processScreenshot(bitmap: Bitmap) {
CoroutineScope(Dispatchers.Default).launch {
// Stage 1: Classify
val classification = nsfwClassifier.classify(bitmap)

if (shouldTriggerCapture(classification)) {
// Stage 2: Detect persons
val boundingBoxes = personDetector.detect(bitmap)

// Process and save
val pixelatedBitmap = ScreenshotProcessor.pixelate(bitmap, boundingBoxes)
val encryptedData = EncryptionManager.encrypt(pixelatedBitmap)
val filePath = StorageManager.saveScreenshot(encryptedData)

// Log event
val event = DetectionEvent(
timestamp = System.currentTimeMillis(),
detectedClass = classification.detectedClass,
severity = classification.severity,
confidence = classification.confidence,
screenshotPath = filePath,
appName = getCurrentAppName()
)
DatabaseManager.insertEvent(event)

// Clean up
pixelatedBitmap.recycle()
}

bitmap.recycle()
}
}

private fun shouldTriggerCapture(classification: ClassificationResult): Boolean {
return when (classification.detectedClass) {
"porn" -> classification.confidence > 0.7
"sexy" -> classification.confidence > 0.8
else -> false
}
}

private fun getCurrentAppName(): String {
// Return current foreground app name
val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val time = System.currentTimeMillis()
val stats = usageStatsManager.queryUsageStats(
UsageStatsManager.INTERVAL_DAILY,
time - 1000 * 10,
time
)
return stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: "unknown"
}
}
```

### Accessibility Service Configuration (accessibility_service_config.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
android:description="@string/accessibility_service_description"
android:packageNames="com.android.chrome,org.mozilla.firefox,com.facebook.katana"
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
android:accessibilityFlags="flagDefault|flagReportViewIds"
android:canRetrieveWindowContent="true"
android:canTakeScreenshot="true"
android:notificationTimeout="100" />
```

### Legacy ScreenCaptureService.kt (DEPRECATED - kept for reference)

**Note:** The MediaProjection-based approach below is replaced by AccessibilityService above. Kept for documentation purposes only.

```kotlin
// OLD IMPLEMENTATION - DO NOT USE
class ScreenCaptureService : Service() {
// ... (previous MediaProjection implementation)
}
```

### ModelUtils.kt

```kotlin
object ModelUtils {
    fun isModelAvailable(assetManager: AssetManager): Boolean {
        return try {
            assetManager.open("nsfw_mobilenet_v2.tflite").close()
            true
        } catch (e: IOException) {
            false
        }
    }
}
```

**Note:** Called in `MainActivity.onCreate()` before any initialization. If the model is missing, the app displays a fatal error dialog with message "ML model file not found. Please contact the developer." and exits immediately.

### NsfwClassifier.kt

```kotlin
class NsfwClassifier(private val assetManager: AssetManager) {
    
    private var interpreter: Interpreter? = null
    private val inputSize = 224
    private val classes = listOf("drawings", "hentai", "neutral", "porn", "sexy")
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        val model = assetManager.open("nsfw_mobilenet_v2.tflite").use { it.readBytes() }
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            useNNAPI(true) // Use neural network acceleration
        }
        interpreter = Interpreter(model, options)
    }
    
    fun classify(bitmap: Bitmap): ClassificationResult {
        // Resize to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        // Convert to float array and normalize
        val inputArray = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
        
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resizedBitmap.getPixel(x, y)
                inputArray[0][y][x][0] = (pixel shr 16 and 0xFF) / 255.0f
                inputArray[0][y][x][1] = (pixel shr 8 and 0xFF) / 255.0f
                inputArray[0][y][x][2] = (pixel and 0xFF) / 255.0f
            }
        }
        
        resizedBitmap.recycle()
        
        // Run inference
        val outputArray = Array(1) { FloatArray(classes.size) }
        interpreter?.run(inputArray, outputArray)
        
        // Get class with highest confidence
        val probabilities = outputArray[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        
        return ClassificationResult(
            detectedClass = classes[maxIndex],
            confidence = probabilities[maxIndex],
            severity = getSeverity(classes[maxIndex], probabilities[maxIndex])
        )
    }
    
    private fun getSeverity(detectedClass: String, confidence: Float): String {
        return when {
            detectedClass == "porn" && confidence > 0.7 -> "critical"
            detectedClass == "sexy" && confidence > 0.8 -> "warning"
            detectedClass in listOf("drawings", "hentai") -> "info"
            else -> "neutral"
        }
    }
    
    data class ClassificationResult(
        val detectedClass: String,
        val confidence: Float,
        val severity: String
    )
}
```

### ScreenshotProcessor.kt

```kotlin
object ScreenshotProcessor {
    
    fun pixelate(originalBitmap: Bitmap, boundingBoxes: List<BoundingBox>): Bitmap {
        val result = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        for (box in boundingBoxes) {
            // Calculate pixelation block size based on box dimensions
            val blockSize = maxOf(20, minOf(box.width, box.height) / 20)
            
            // Create pixelated version of region
            val region = Bitmap.createBitmap(
                originalBitmap,
                box.left, box.top, box.width, box.height
            )
            val pixelatedRegion = applyPixelation(region, blockSize)
            
            // Draw pixelated region back
            canvas.drawBitmap(pixelatedRegion, box.left.toFloat(), box.top.toFloat(), paint)
            
            region.recycle()
            pixelatedRegion.recycle()
        }
        
        return result
    }
    
    private fun applyPixelation(bitmap: Bitmap, blockSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        for (y in 0 until height step blockSize) {
            for (x in 0 until width step blockSize) {
                val blockWidth = minOf(blockSize, width - x)
                val blockHeight = minOf(blockSize, height - y)
                
                // Sample color from center of block
                val centerX = x + blockWidth / 2
                val centerY = y + blockHeight / 2
                val color = bitmap.getPixel(centerX, centerY)
                
                // Draw block
                val paint = Paint().apply { this.color = color }
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + blockWidth).toFloat(), (y + blockHeight).toFloat(),
                    paint
                )
            }
        }
        
        return result
    }
    
    fun compressToWebP(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
        return stream.toByteArray()
    }
}

data class BoundingBox(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val confidence: Float
)
```

### EncryptionManager.kt

```kotlin
object EncryptionManager {
    
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    
    private lateinit var masterKey: SecretKey
    
    fun initialize(context: Context) {
        masterKey = getOrCreateMasterKey(context)
    }
    
    private fun getOrCreateMasterKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        // Try to get existing key
        val existingKey = keyStore.getEntry("oathkeeper_master_key", null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }
        
        // Create new key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            "oathkeeper_master_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
    
    fun encrypt(plaintext: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(iv, ciphertext)
    }
    
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    data class EncryptedData(
        val iv: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun toByteArray(): ByteArray {
            return iv + ciphertext
        }
        
        companion object {
            fun fromByteArray(data: ByteArray): EncryptedData {
                val iv = data.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
                return EncryptedData(iv, ciphertext)
            }
        }
    }
}
```

## Dependencies (build.gradle)

```gradle
dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    
    // TensorFlow Lite
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    implementation 'org.tensorflow:tensorflow-lite-gpu:2.14.0'
    implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
    
    // Database encryption
    implementation 'net.zetetic:android-database-sqlcipher:4.5.4'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // WebView
    implementation 'androidx.webkit:webkit:1.9.0'
    
    // PDF generation
    implementation 'com.itextpdf:itext7-core:8.0.2'
    
    // JSON
    implementation 'org.json:json:20231013'
    
    // Biometric authentication
    implementation 'androidx.biometric:biometric:1.1.0'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

## Database Schema (SQLCipher)

```sql
-- Events table stores all detection events
CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,              -- Unix timestamp in milliseconds
    detected_class TEXT NOT NULL,            -- 'porn', 'sexy', 'drawings', 'hentai'
    severity TEXT NOT NULL,                  -- 'critical', 'warning', 'info'
    confidence REAL NOT NULL,                -- 0.0 to 1.0
    screenshot_path TEXT,                    -- Path to encrypted screenshot file
    app_name TEXT,                           -- Name of app being used
    is_reviewed BOOLEAN DEFAULT 0,           -- User has reviewed this event
    notes TEXT,                              -- User notes
    created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

-- Index for fast date range queries
CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_events_severity ON events(severity);

-- Tamper events table
CREATE TABLE tamper_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    event_type TEXT NOT NULL,                -- 'service_stopped', 'permission_revoked', etc.
    details TEXT,                            -- JSON with additional details
    recovered BOOLEAN DEFAULT 0,             -- Whether service recovered
    created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

-- Settings table
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

-- Insert default settings
INSERT INTO settings (key, value) VALUES
    ('capture_interval_ms', '2000'),
    ('nsfw_threshold_porn', '0.7'),
    ('nsfw_threshold_sexy', '0.8'),
    ('retention_days', '0'),                  -- 0 means keep forever
    ('enable_tamper_warnings', '1'),
    ('enable_notifications', '1');
```

## Model Conversion Script (Python)

```python
#!/usr/bin/env python3
"""
Convert NSFW detection models to TensorFlow Lite format for Android.
"""

import tensorflow as tf
import numpy as np
from pathlib import Path

def convert_nsfw_model():
    """Convert GantMan/nsfw_model MobileNet V2 to TFLite"""
    
    # Load model
    model_path = "models/nsfw_mobilenet_v2_224x224.h5"
    model = tf.keras.models.load_model(model_path)
    
    # Convert to TFLite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Apply optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Enable quantization (reduces model size by 4x)
    converter.target_spec.supported_types = [tf.int8]
    
    # Representative dataset for quantization calibration
    def representative_dataset():
        for _ in range(100):
            # Generate random data in the correct shape
            data = np.random.rand(1, 224, 224, 3).astype(np.float32)
            yield [data]
    
    converter.representative_dataset = representative_dataset
    
    # Convert
    tflite_model = converter.convert()
    
    # Save
    output_path = "android/app/src/main/assets/nsfw_mobilenet_v2.tflite"
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"Model converted and saved to {output_path}")
    print(f"Model size: {len(tflite_model) / 1024 / 1024:.2f} MB")

def convert_yolo_model():
    """Convert YOLOv8-nano to TFLite"""
    from ultralytics import YOLO
    
    # Load model
    model = YOLO('yolov8n.pt')
    
    # Export to TFLite
    model.export(format='tflite', int8=True)
    
    # Move to assets
    import shutil
    src = 'yolov8n_saved_model/yolov8n_int8.tflite'
    dst = 'android/app/src/main/assets/yolov8n_person.tflite'
    Path(dst).parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(src, dst)
    
    print(f"YOLO model saved to {dst}")

if __name__ == "__main__":
    convert_nsfw_model()
    convert_yolo_model()
```

## Performance Optimization Tips

1. **Model Inference**
   - Use GPU delegate for TensorFlow Lite on supported devices
   - Run inference on background thread pool
   - Batch frames when possible
   - Use INT8 quantized models (4x smaller, 2-3x faster)

2. **Screen Capture**
   - Capture at reduced resolution for ML (224x224)
   - Only capture full resolution on positive detection
   - Use ImageReader with 2-3 frame buffer
   - Pause capture when screen is off or app not in foreground

3. **Storage**
   - Use WebP format (20-30% smaller than JPEG)
   - Compress screenshots to quality 80
   - Implement LRU cache for recently viewed images
   - Auto-delete old screenshots based on retention policy

4. **Battery**
   - Adaptive capture rate (slower when idle, faster in browsers)
   - Reduce inference frequency when device is low on battery
   - Use JobScheduler for background database maintenance
   - Implement doze mode awareness

5. **UI**
   - Use RecyclerView with ViewHolder pattern for lists
   - Load images asynchronously with Glide or Coil
   - Implement pagination for large datasets
   - Use WebView with hardware acceleration for reports
