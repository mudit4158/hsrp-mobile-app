package com.hsrp.dealer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hsrp.dealer.BuildConfig
import java.io.File
import java.io.FileOutputStream

fun photoFile(context: Context, kind: String): File =
    File(context.filesDir, "photos/$kind-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }

/** Shrinks a captured photo to at most 1600 px on the long side, upright (EXIF rotation applied), JPEG quality 80. */
fun shrinkPhoto(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
    if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
}

/** Debug builds only: a generated image for emulators without a camera. */
fun testPhoto(context: Context, kind: String, label: String): File {
    val bmp = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.rgb(230, 236, 245))
    val paint = Paint().apply { color = android.graphics.Color.rgb(30, 58, 138); textSize = 48f; isAntiAlias = true }
    canvas.drawText("TEST PHOTO · $kind", 40f, 120f, paint)
    canvas.drawText(label, 40f, 220f, paint)
    val file = photoFile(context, kind)
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    return file
}

@Composable
fun CameraCapture(kind: String, label: String, onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    if (granted) {
        DisposableEffect(owner) {
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            future.addListener({
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider?.unbindAll()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                } catch (e: Exception) {
                    error = "Camera not available: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose { provider?.unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label photo", color = Color.White)
            if (!granted) Text("Allow camera access to take the photo.", color = Color.White)
            error?.let { Text(it, color = Color(0xFFFCA5A5)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = granted && !busy && error == null,
                    onClick = {
                        busy = true
                        val file = photoFile(context, kind)
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    shrinkPhoto(file)
                                    busy = false
                                    onCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    error = "Could not take the photo: ${exception.message}"
                                }
                            },
                        )
                    },
                ) { Text(if (busy) "Saving…" else "Take photo") }
                if (!granted) OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera", color = Color.White) }
                if (BuildConfig.DEBUG) OutlinedButton(onClick = { onCaptured(testPhoto(context, kind, label)) }) { Text("Test photo", color = Color.White) }
                OutlinedButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
            }
        }
    }
}
