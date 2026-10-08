package com.photoprint.pro.platform

import android.content.Intent
import android.net.Uri

/**
 * What the gateway needs from the foreground Activity: the system pickers must be launched through
 * Activity Result launchers registered by the Activity itself.
 */
interface AndroidHost {
    /** Opens the system photo picker. Empty if the user backs out. */
    suspend fun pickImages(max: Int): List<Uri>

    /** Opens the camera app, writing a full-size photo to [output]. */
    suspend fun takePicture(output: Uri): Boolean

    /** Opens the system "save as" screen. Null if the user backs out. */
    suspend fun createDocument(mimeType: String, suggestedName: String): Uri?

    /** Starts [intent]; false if no app can handle it. */
    fun launch(intent: Intent): Boolean

    /** The Activity as a Context, for system services that want one (PrintManager). */
    val activityContext: android.content.Context
}
