package com.naira.assistant.core.tools.executor

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/** Outcome of a picture tool call: the JSON response plus optional display image bytes. */
data class RandomPictureResult(
    val response: JsonObject,
    val image: ByteArray?,
)

/**
 * Local randomness tools. Every random value comes from a device-side
 * [SecureRandom]; the model only chooses which tool to call, never the value.
 */
object RandomToolExecutor {

    private val random = SecureRandom()

    private const val DEFAULT_MIN = 1L
    private const val DEFAULT_MAX = 100L
    private const val MAX_RANGE_SIZE = 1_000_000_000L
    private const val MAX_PICTURE_DIMENSION = 1024
    private const val PICTURE_JPEG_QUALITY = 85

    // ---------------------------------------------------------
    // random_number
    // ---------------------------------------------------------

    fun executeRandomNumber(call: FunctionCall): JsonObject {
        val min = numberArg(call, "min") ?: DEFAULT_MIN
        val max = numberArg(call, "max") ?: DEFAULT_MAX

        if (min > max) {
            return error(
                "'min' ($min) must be less than or equal to 'max' ($max). " +
                        "Swap the bounds and call again."
            )
        }

        val size = max - min + 1
        if (size < 1 || size > MAX_RANGE_SIZE) {
            return error(
                "Range too large. Keep max - min below 1,000,000,000 values."
            )
        }

        val number = min + random.nextInt(size.toInt())

        return buildJsonObject {
            put("success", true)
            put("number", number)
            put("min", min)
            put("max", max)
            put("note", "True device-side random value; do not change or round it.")
        }
    }

    // ---------------------------------------------------------
    // random_boolean
    // ---------------------------------------------------------

    fun executeRandomBoolean(call: FunctionCall): JsonObject {
        val result = random.nextBoolean()

        val guessArg = call.args["guess"]
        val guess = when {
            guessArg == null || guessArg is JsonNull -> null
            else -> parseGuess(guessArg)
                ?: return error(
                    "'guess' must be a boolean (true/false) or 'heads'/'tails'."
                )
        }

        return buildJsonObject {
            put("success", true)
            put("result", result)
            put("result_label", if (result) "Yes/Heads" else "No/Tails")
            if (guess != null) {
                put("guess", guess)
                put("correct", guess == result)
            }
            put("note", "True device-side coin flip; do not change the outcome.")
        }
    }

    // ---------------------------------------------------------
    // random_picture
    // ---------------------------------------------------------

    fun executeRandomPicture(call: FunctionCall, context: Context): RandomPictureResult {
        val appContext = context.applicationContext

        if (!hasImageReadPermission(appContext)) {
            return RandomPictureResult(
                error(
                    "Photo permission is not granted. Ask the user to enable " +
                            "Photos/media access in Settings → Permissions, then try again."
                ),
                null,
            )
        }

        val imageIds = queryImageIds(appContext)
        if (imageIds.isEmpty()) {
            return RandomPictureResult(
                error("No photos were found in the device's picture library."),
                null,
            )
        }

        val chosenUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageIds[random.nextInt(imageIds.size)],
        )

        return try {
            val picture = loadDisplayImage(appContext, chosenUri)
            RandomPictureResult(
                buildJsonObject {
                    put("success", true)
                    put("name", picture.name)
                    picture.folder?.let { put("folder", it) }
                    put("width", picture.width)
                    put("height", picture.height)
                    put("note", "The random picture is attached after this result.")
                },
                picture.bytes,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RandomPictureResult(
                error(
                    "Couldn't read the selected photo. It may have been moved or " +
                            "deleted; try again."
                ),
                null,
            )
        }
    }

    // ---------------------------------------------------------
    // Argument parsing
    // ---------------------------------------------------------

    private fun numberArg(call: FunctionCall, key: String): Long? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        primitive.longOrNull?.let { return it }
        if (primitive.isString) return primitive.content.trim().toLongOrNull()
        return primitive.doubleOrNull
            ?.takeIf { it.isFinite() && it % 1.0 == 0.0 }
            ?.toLong()
    }

    private fun parseGuess(element: kotlinx.serialization.json.JsonElement): Boolean? {
        val primitive = element as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.content.trim().lowercase()) {
            "true", "yes", "heads" -> true
            "false", "no", "tails" -> false
            else -> null
        }
    }

    // ---------------------------------------------------------
    // Picture loading
    // ---------------------------------------------------------

    private data class DisplayImage(
        val bytes: ByteArray,
        val name: String,
        val folder: String?,
        val width: Int,
        val height: Int,
    )

    private fun queryImageIds(context: Context): LongArray {
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idColumn))
            }
        }
        return ids.toLongArray()
    }

    private fun loadDisplayImage(context: Context, uri: Uri): DisplayImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeStream(context, uri, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Unreadable photo")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        var bitmap = decodeStream(context, uri, options)
            ?: throw IllegalStateException("Couldn't decode photo")
        bitmap = applyExifRotation(context, uri, bitmap)
        bitmap = scaleToFit(bitmap, MAX_PICTURE_DIMENSION)

        val bytes = ByteArrayOutputStream().also { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, PICTURE_JPEG_QUALITY, output)
        }.toByteArray()
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()

        return DisplayImage(
            bytes = bytes,
            name = queryDetail(context, uri, MediaStore.Images.Media.DISPLAY_NAME) ?: "photo",
            folder = queryDetail(context, uri, MediaStore.Images.Media.RELATIVE_PATH),
            width = width,
            height = height,
        )
    }

    private fun decodeStream(
        context: Context,
        uri: Uri,
        options: BitmapFactory.Options,
    ): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= MAX_PICTURE_DIMENSION) {
            sample *= 2
            largest /= 2
        }
        return sample
    }

    private fun scaleToFit(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * Decoded bitmaps ignore EXIF orientation, so camera photos would display
     * sideways. Applies the rotation (and mirrored variants) from the photo's
     * EXIF metadata.
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE ->
                matrix.postRotate(90f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)

            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE ->
                matrix.postRotate(270f)
        }
        if (orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        ) {
            matrix.postScale(1f, -1f)
        }

        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun queryDetail(context: Context, uri: Uri, column: String): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(column),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(column)) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun hasImageReadPermission(context: Context): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }
}
