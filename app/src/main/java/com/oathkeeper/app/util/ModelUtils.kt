package com.oathkeeper.app.util

import android.content.res.AssetManager
import java.io.IOException

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
