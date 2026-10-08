package com.photoprint.pro.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.photoprint.pro.ui.PhotoImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads stored photos for display: downsampled, EXIF-rotated, off the main thread. */
class AndroidImageSource : PhotoImageSource {
    override suspend fun load(ref: String, maxSidePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            PhotoFiles.decodeUpright(File(ref), maxSidePx)?.asImageBitmap()
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
