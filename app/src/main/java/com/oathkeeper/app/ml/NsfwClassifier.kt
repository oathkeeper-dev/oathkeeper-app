package com.oathkeeper.app.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NsfwClassifier(private val assetManager: AssetManager) {
    
    private var interpreter: Interpreter? = null
    private val inputSize = 224
    private val numClasses = 5
    private val classes = listOf("drawings", "hentai", "neutral", "porn", "sexy")
    
    companion object {
        private const val TAG = "Oathkeeper"
        private const val MODEL_FILE = "nsfw_mobilenet_v2.tflite"
    }
    
    data class ClassificationResult(
        val detectedClass: String,
        val confidence: Float,
        val severity: String,
        val allScores: Map<String, Float>
    )
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                // Use NNAPI for acceleration when available
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    try {
                        val nnApiDelegate = NnApiDelegate()
                        addDelegate(nnApiDelegate)
                        Log.d(TAG, "NNAPI delegate added")
                    } catch (e: Exception) {
                        Log.w(TAG, "NNAPI not available, using CPU")
                        setNumThreads(4)
                    }
                } else {
                    setNumThreads(4)
                }
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "NSFW model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            throw IOException("Failed to load TFLite model", e)
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    suspend fun classify(bitmap: Bitmap): ClassificationResult = withContext(Dispatchers.Default) {
        val inputBuffer = preprocess(bitmap)
        val outputBuffer = Array(1) { FloatArray(numClasses) }
        
        interpreter?.run(inputBuffer, outputBuffer)
            ?: throw IllegalStateException("Interpreter not initialized")
        
        val scores = outputBuffer[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val detectedClass = classes[maxIndex]
        val confidence = scores[maxIndex]
        val severity = determineSeverity(detectedClass, confidence)
        
        val allScores = classes.zip(scores.toList()).toMap()
        
        ClassificationResult(
            detectedClass = detectedClass,
            confidence = confidence,
            severity = severity,
            allScores = allScores
        )
    }
    
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            // Normalize to [0, 1] and add to buffer in RGB order
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
            inputBuffer.putFloat((pixel and 0xFF) / 255.0f)          // B
        }
        
        resized.recycle()
        return inputBuffer
    }
    
    private fun determineSeverity(detectedClass: String, confidence: Float): String {
        return when (detectedClass) {
            "porn" -> when {
                confidence > 0.7f -> "CRITICAL"
                confidence > 0.5f -> "WARNING"
                else -> "INFO"
            }
            "sexy" -> when {
                confidence > 0.8f -> "WARNING"
                confidence > 0.6f -> "INFO"
                else -> "LOW"
            }
            "hentai" -> when {
                confidence > 0.7f -> "WARNING"
                else -> "INFO"
            }
            "drawings" -> "INFO"
            else -> "LOW"
        }
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
