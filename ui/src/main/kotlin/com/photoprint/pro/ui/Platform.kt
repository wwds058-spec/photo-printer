package com.photoprint.pro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/** Loads a photo for display. Implemented on Android with BitmapFactory (downsampled, EXIF-rotated). */
interface PhotoImageSource {
    /** @return the image scaled so its longest side is at most [maxSidePx], or null if it can't be read. */
    suspend fun load(ref: String, maxSidePx: Int): ImageBitmap?
}

/** Used by previews/tests that have no real images. */
object NoImages : PhotoImageSource {
    override suspend fun load(ref: String, maxSidePx: Int): ImageBitmap? = null
}

val LocalImageSource = compositionLocalOf<PhotoImageSource> { NoImages }

/** Loads one photo, returning null until ready (or if unreadable). */
@Composable
fun rememberPhotoBitmap(ref: String, maxSidePx: Int): State<ImageBitmap?> {
    val source = LocalImageSource.current
    return produceState<ImageBitmap?>(initialValue = null, ref, maxSidePx, source) {
        value = source.load(ref, maxSidePx)
    }
}

/** Loads several photos keyed by photo id; entries appear as they finish. */
@Composable
fun rememberPhotoBitmaps(refs: Map<String, String>, maxSidePx: Int): State<Map<String, ImageBitmap>> {
    val source = LocalImageSource.current
    val state = remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val key = refs.toList()
    androidx.compose.runtime.LaunchedEffect(key, maxSidePx, source) {
        val loaded = HashMap<String, ImageBitmap>()
        for ((id, ref) in refs) {
            source.load(ref, maxSidePx)?.let { loaded[id] = it }
            state.value = HashMap(loaded)
        }
        state.value = loaded
    }
    return state
}

