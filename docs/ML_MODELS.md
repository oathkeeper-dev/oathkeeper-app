# Machine Learning Models

## Overview

Oathkeeper uses a two-stage machine learning pipeline for on-device content detection:

1. **Stage 1**: NSFW Classification (detects IF content is inappropriate)
2. **Stage 2**: Person Detection (finds WHERE to apply pixelation)

Both models run locally using TensorFlow Lite and require no network connectivity.

---

## Stage 1: NSFW Classifier

### Model: GantMan/nsfw_model (MobileNet V2)

**Repository**: https://github.com/GantMan/nsfw_model

**Why this model?**
- Well-maintained with 2,000+ GitHub stars
- Multiple size options (MobileNet V2 for mobile)
- Good accuracy (~93%) on diverse content
- Easy to convert to TFLite
- 5-class output provides granular detection

### Specifications

| Attribute | Value |
|-----------|-------|
| Base Model | MobileNet V2 |
| Input Size | 224x224 pixels |
| Output Classes | 5 |
| Original Size | ~4-6 MB (Keras) |
| TFLite Size | ~2-3 MB (INT8 quantized) |
| Inference Time | ~50ms on modern Android devices |
| Accuracy | ~93% on validation set |

### Output Classes

The model outputs probabilities for 5 categories:

1. **`drawings`** - Safe drawings and illustrations
2. **`hentai`** - Hentai/animated adult content
3. **`neutral`** - Safe photos (default category)
4. **`porn`** - Pornographic content (most explicit)
5. **`sexy`** - Sexually suggestive but non-explicit

### Trigger Thresholds

Based on our requirements, we use these thresholds:

| Class | Threshold | Severity |
|-------|-----------|----------|
| `porn` | > 0.7 | **Critical** |
| `sexy` | > 0.8 | **Warning** |
| `drawings` | > 0.9 | **Info** |
| `hentai` | > 0.9 | **Info** |

**Rationale**: 
- Lower threshold for `porn` to catch explicit content
- Higher threshold for `sexy` to reduce false positives on swimwear/fitness
- Very high threshold for `drawings`/`hentai` as they're less severe

### Conversion Process

```python
import tensorflow as tf

# Load Keras model
model = tf.keras.models.load_model('nsfw_mobilenet_v2_224x224.h5')

# Create TFLite converter
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Apply optimizations
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Enable INT8 quantization
converter.target_spec.supported_types = [tf.int8]

# Representative dataset for calibration
def representative_dataset():
    for _ in range(100):
        data = np.random.rand(1, 224, 224, 3).astype(np.float32)
        yield [data]

converter.representative_dataset = representative_dataset

# Convert
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS_INT8
]

# Generate model
tflite_model = converter.convert()

# Save
with open('nsfw_mobilenet_v2.tflite', 'wb') as f:
    f.write(tflite_model)
```

**Output**: `nsfw_mobilenet_v2.tflite` (~2-3 MB)

### Preprocessing

```kotlin
// Resize bitmap to 224x224
val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

// Normalize pixel values to [0, 1]
val inputArray = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
for (y in 0 until 224) {
    for (x in 0 until 224) {
        val pixel = resizedBitmap.getPixel(x, y)
        inputArray[0][y][x][0] = (pixel shr 16 and 0xFF) / 255.0f  // R
        inputArray[0][y][x][1] = (pixel shr 8 and 0xFF) / 255.0f   // G
        inputArray[0][y][x][2] = (pixel and 0xFF) / 255.0f         // B
    }
}
```

### Postprocessing

```kotlin
// Get class with highest probability
val probabilities = outputArray[0]
val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
val detectedClass = classes[maxIndex]
val confidence = probabilities[maxIndex]

// Check threshold
val shouldTrigger = when (detectedClass) {
    "porn" -> confidence > 0.7
    "sexy" -> confidence > 0.8
    "drawings", "hentai" -> confidence > 0.9
    else -> false
}
```

---

## Stage 2: Person Detector

### Model: YOLOv8-nano

**Source**: Ultralytics YOLOv8

**Why this model?**
- State-of-the-art object detection
- Very small model size (nano variant)
- Fast inference on mobile devices
- Provides bounding boxes needed for smart pixelation
- Easy to convert to TFLite

### Specifications

| Attribute | Value |
|-----------|-------|
| Base Model | YOLOv8-nano |
| Input Size | 640x640 pixels |
| Output | Bounding boxes + class probabilities |
| Original Size | ~6 MB (PyTorch) |
| TFLite Size | ~3.5 MB (INT8 quantized) |
| Inference Time | ~30ms on modern Android devices |
| mAP (COCO) | 37.3% |

### Classes

YOLOv8 is trained on COCO dataset with 80 classes. We only use:

- **`person`** (class 0) - Detects people in the image

All other classes are ignored.

### Conversion Process

```python
from ultralytics import YOLO

# Load pretrained YOLOv8-nano
model = YOLO('yolov8n.pt')

# Export to TFLite with INT8 quantization
model.export(
    format='tflite',
    int8=True,
    imgsz=640
)

# Output: yolov8n_saved_model/yolov8n_int8.tflite
```

### Postprocessing

YOLO output format (per detection):
```
[x_center, y_center, width, height, confidence, class_0, class_1, ..., class_79]
```

We filter for person class:

```kotlin
fun parseDetections(output: Array<FloatArray>): List<BoundingBox> {
    val boxes = mutableListOf<BoundingBox>()
    val threshold = 0.5  // Confidence threshold
    
    for (detection in output) {
        val confidence = detection[4]
        if (confidence < threshold) continue
        
        // Get class with highest probability
        val classScores = detection.slice(5 until detection.size)
        val classId = classScores.indices.maxByOrNull { classScores[it] } ?: -1
        
        // Only keep person detections (class 0)
        if (classId == 0) {
            val x = detection[0]
            val y = detection[1]
            val w = detection[2]
            val h = detection[3]
            
            boxes.add(BoundingBox(
                left = (x - w/2).toInt(),
                top = (y - h/2).toInt(),
                width = w.toInt(),
                height = h.toInt(),
                confidence = confidence
            ))
        }
    }
    
    return boxes
}
```

### Non-Maximum Suppression (NMS)

To remove overlapping detections:

```kotlin
fun applyNMS(boxes: List<BoundingBox>, iouThreshold: Float = 0.5): List<BoundingBox> {
    val sorted = boxes.sortedByDescending { it.confidence }
    val result = mutableListOf<BoundingBox>()
    
    for (box in sorted) {
        var shouldKeep = true
        for (kept in result) {
            if (calculateIoU(box, kept) > iouThreshold) {
                shouldKeep = false
                break
            }
        }
        if (shouldKeep) result.add(box)
    }
    
    return result
}

fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
    val x1 = maxOf(box1.left, box2.left)
    val y1 = maxOf(box1.top, box2.top)
    val x2 = minOf(box1.left + box1.width, box2.left + box2.width)
    val y2 = minOf(box1.top + box1.height, box2.top + box2.height)
    
    val intersection = maxOf(0, x2 - x1) * maxOf(0, y2 - y1)
    val area1 = box1.width * box1.height
    val area2 = box2.width * box2.height
    val union = area1 + area2 - intersection
    
    return intersection.toFloat() / union
}
```

---

## Two-Stage Pipeline

### Complete Flow

```
Screen Frame (native resolution)
         │
         ├──▶ Resize to 224x224 ──▶ NSFW Classifier
         │                              │
         │                              ▼
         │                    [porn > 0.7] OR [sexy > 0.8]?
         │                              │
         │                    YES ──────┴────── NO
         │                    │                 │
         │                    ▼                 ▼
         │         Person Detection       Discard frame
         │         (on original resolution)
         │                    │
         │                    ▼
         │         Get bounding boxes
         │                    │
         │                    ▼
         └──────────▶ Apply pixelation to regions
                           │
                           ▼
                    Save encrypted screenshot
                           │
                           ▼
                    Log detection event
```

### Performance Optimization

1. **Skip Person Detection for Low Confidence**
   - If NSFW confidence is borderline (e.g., 0.71 for porn), skip expensive person detection
   - Only run YOLO when confidence > 0.85

2. **Frame Skipping**
   - Don't run ML on every frame
   - Process every 2nd or 3rd frame
   - Use last known bounding boxes if content is similar

3. **Model Caching**
   - Keep TFLite interpreter in memory
   - Reuse input/output buffers
   - Use `useNNAPI(true)` for acceleration on compatible devices

4. **Batch Processing**
   - If multiple frames are ready, process them sequentially on background thread
   - Avoid blocking main thread

---

## Model Files

### Repository Structure

```
android/app/src/main/assets/
├── nsfw_mobilenet_v2.tflite          (2-3 MB)
├── yolov8n_person.tflite              (3.5 MB)
└── labels.txt                         (Class labels)
```

### labels.txt

```
drawings
hentai
neutral
porn
sexy
```

### Git LFS

Add to `.gitattributes`:
```
*.tflite filter=lfs diff=lfs merge=lfs -text
```

This prevents large model files from bloating the Git repository.

---

## Alternative Models Considered

### Classification Alternatives

| Model | Size | Accuracy | Why Not Used |
|-------|------|----------|--------------|
| Falconsai ViT | 344 MB | 98% | Too large for mobile |
| Marqo ViT-Tiny | 44 MB | 98.5% | PyTorch only, complex conversion |
| Yahoo open_nsfw | 25 MB | 90% | Outdated, lower accuracy |
| NudeNet | 50 MB | N/A | Object detection, not classification |

### Detection Alternatives

| Model | Size | Speed | Why Not Used |
|-------|------|-------|--------------|
| YOLOv5-nano | 1.9 MB | Fast | YOLOv8 has better accuracy |
| MobileNet SSD | 15 MB | Medium | Less accurate than YOLO |
| EfficientDet-D0 | 15 MB | Slow | Slower inference |
| MediaPipe Pose | 5 MB | Fast | Only detects pose keypoints, not full person |

---

## Testing & Validation

### Accuracy Testing

```kotlin
@Test
fun testNsfwClassifier() {
    val classifier = NsfwClassifier(assetManager)
    
    // Test with known safe image
    val safeBitmap = loadTestImage("test_safe.jpg")
    val safeResult = classifier.classify(safeBitmap)
    assertEquals("neutral", safeResult.detectedClass)
    assertTrue(safeResult.confidence > 0.9)
    
    // Test with known explicit image
    val explicitBitmap = loadTestImage("test_explicit.jpg")
    val explicitResult = classifier.classify(explicitBitmap)
    assertEquals("porn", explicitResult.detectedClass)
    assertTrue(explicitResult.confidence > 0.8)
}
```

### Performance Testing

```kotlin
@Test
fun testInferenceSpeed() {
    val classifier = NsfwClassifier(assetManager)
    val detector = PersonDetector(assetManager)
    val bitmap = createTestBitmap(1920, 1080)
    
    // Warmup
    repeat(10) { classifier.classify(bitmap) }
    
    // Measure
    val startTime = System.currentTimeMillis()
    repeat(100) { 
        classifier.classify(bitmap)
        detector.detect(bitmap)
    }
    val endTime = System.currentTimeMillis()
    
    val avgTime = (endTime - startTime) / 100.0
    println("Average inference time: ${avgTime}ms")
    assertTrue(avgTime < 100) // Must be under 100ms
}
```

---

## Future Improvements

1. **Custom Model Training**
   - Train on specific categories (swimwear, underwear)
   - Improve accuracy for target use case
   - Reduce false positives on fitness content

2. **Model Compression**
   - Pruning to reduce model size
   - Knowledge distillation
   - Neural architecture search

3. **Dynamic Thresholds**
   - Adjust thresholds based on time of day
   - User feedback loop for false positives
   - Context-aware detection (browser vs. gallery)

4. **Multi-Model Ensemble**
   - Combine multiple classifiers for better accuracy
   - Use confidence voting
   - Reduce false positive rate

---

## Resources

- **GantMan/nsfw_model**: https://github.com/GantMan/nsfw_model
- **Ultralytics YOLOv8**: https://github.com/ultralytics/ultralytics
- **TensorFlow Lite Guide**: https://www.tensorflow.org/lite/guide
- **TFLite Model Optimization**: https://www.tensorflow.org/lite/performance/model_optimization
- **Android NNAPI**: https://developer.android.com/ndk/guides/neuralnetworks
