package com.naira.assistant.presentation.main.component

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fullscreen in-app image viewer for every image a chat bubble can carry:
 * captured screenshots (`file://` in app-private cache), generated and picked
 * photos (`content://`), tool images (`data:`), and remote images (`https://`).
 *
 * Opening these with an external ACTION_VIEW intent is not an option: on
 * Android 7+ sharing a `file://` URI outside the app crashes with
 * FileUriExposedException, and other apps cannot read app-private cache files
 * anyway. Pinch to zoom, double-tap to toggle zoom; Save/Share export
 * app-private images into the public gallery first.
 */
@Composable
fun ImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }

    val canExport = isLocalImageSource(imageUrl)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load this image.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                        )
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close viewer",
                        tint = Color.White,
                    )
                }
                if (canExport) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                        )
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                busy = true
                                val saved = saveToGallery(context, imageUrl)
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (saved) "Saved to Pictures/Naira" else "Couldn't save image.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.SaveAlt,
                                contentDescription = "Save to gallery",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                busy = true
                                shareImage(context, imageUrl)
                                busy = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share image",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Only locally readable sources can be exported; remote images display only. */
private fun isLocalImageSource(imageUrl: String): Boolean =
    imageUrl.startsWith("data:") ||
            imageUrl.startsWith("file://") ||
            imageUrl.startsWith("content://")

/** Reads the raw image bytes for local sources, or null when unavailable. */
private suspend fun materializeImageBytes(context: Context, imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            when {
                imageUrl.startsWith("data:") -> {
                    val base64 = imageUrl.substringAfter(";base64,", "")
                    if (base64.isEmpty()) null else Base64.decode(base64, Base64.DEFAULT)
                }

                imageUrl.startsWith("file://") -> {
                    val path = Uri.parse(imageUrl).path ?: return@runCatching null
                    File(path).readBytes()
                }

                imageUrl.startsWith("content://") -> {
                    context.contentResolver
                        .openInputStream(Uri.parse(imageUrl))
                        ?.use { it.readBytes() }
                }

                else -> null
            }
        }.getOrNull()
    }

/** Copies the image into the public Pictures/Naira gallery collection. */
private suspend fun saveToGallery(context: Context, imageUrl: String): Boolean {
    val bytes = materializeImageBytes(context, imageUrl) ?: return false
    return withContext(Dispatchers.IO) {
        runCatching { insertIntoGallery(context, bytes) != null }.getOrDefault(false)
    }
}

/** Shares the image through a public MediaStore URI that other apps may read. */
private suspend fun shareImage(context: Context, imageUrl: String) {
    val bytes = materializeImageBytes(context, imageUrl)
    if (bytes == null) {
        Toast.makeText(context, "Couldn't share image.", Toast.LENGTH_SHORT).show()
        return
    }
    val sharedUri = withContext(Dispatchers.IO) {
        runCatching { insertIntoGallery(context, bytes) }.getOrNull()
    }
    if (sharedUri == null) {
        Toast.makeText(context, "Couldn't share image.", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, sharedUri)
        // ClipData + grant flags make the URI readable by the chosen target.
        clipData = ClipData.newRawUri("image", sharedUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share image"))
    }.onFailure {
        Toast.makeText(context, "No app can share images.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Inserts image bytes into MediaStore as Pictures/Naira/<name>.jpg and returns
 * the public content URI, or null on failure. IS_PENDING is only known on
 * Android 10+; on older versions the image is written directly.
 */
private fun insertIntoGallery(context: Context, bytes: ByteArray): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "naira_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Naira",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return null
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
    } catch (_: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        return null
    }
    return uri
}
