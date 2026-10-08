package com.photoprint.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photoprint.pro.platform.AndroidHost
import com.photoprint.pro.ui.PhotoPrintRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred

@AndroidEntryPoint
class MainActivity : ComponentActivity(), AndroidHost {
    private val viewModel: AppViewModel by viewModels()

    // Activity Result launchers must be registered before the Activity is started.
    private var pendingPick: CompletableDeferred<List<Uri>>? = null
    private var pendingCamera: CompletableDeferred<Boolean>? = null
    private var pendingDocument: CompletableDeferred<Uri?>? = null
    private var pendingMime: String = "application/pdf"

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        pendingPick?.complete(uris)
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingCamera?.complete(ok)
    }
    private val createDocumentLauncher = registerForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: android.content.Context, input: String): Intent =
                super.createIntent(context, input).setType(pendingMime)
        },
    ) { uri -> pendingDocument?.complete(uri) }

    override val activityContext: android.content.Context get() = this

    override suspend fun pickImages(max: Int): List<Uri> {
        val d = CompletableDeferred<List<Uri>>().also { pendingPick = it }
        pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return d.await()
    }

    override suspend fun takePicture(output: Uri): Boolean {
        val d = CompletableDeferred<Boolean>().also { pendingCamera = it }
        takePictureLauncher.launch(output)
        return d.await()
    }

    override suspend fun createDocument(mimeType: String, suggestedName: String): Uri? {
        pendingMime = mimeType
        val d = CompletableDeferred<Uri?>().also { pendingDocument = it }
        createDocumentLauncher.launch(suggestedName)
        return d.await()
    }

    override fun launch(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.host = this
        setContent {
            val nav by viewModel.controller.nav.state.collectAsState()
            // System Back walks the in-app flow first; at the root it exits as usual.
            BackHandler(enabled = nav.canGoBack) { viewModel.controller.nav.pop() }
            PhotoPrintRoot(viewModel.controller, viewModel.imageSource)
        }
    }

    override fun onDestroy() {
        if (viewModel.host === this) viewModel.host = null
        super.onDestroy()
    }
}
