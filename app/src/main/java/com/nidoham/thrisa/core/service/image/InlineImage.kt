package com.nidoham.thrisa.core.service.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Loads a user-attached image as inline request bytes: decoded, EXIF-rotated,
 * downscaled to fit [DEFAULT_MAX_DIM] and re-compressed as JPEG. Small payloads
 * keep vision and image-editing requests fast and well under Gemini's inline
 * request limits.
 */
object InlineImage {

    data class Loaded(val bytes: ByteArray, val mimeType: String) {
        /** Base64 payload for a Gemini `inlineData` part. */
        fun base64(): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    const val DEFAULT_MAX_DIM = 1536
    private const val DEFAULT_QUALITY = 88

    fun load(
        context: Context,
        uri: Uri,
        maxDim: Int = DEFAULT_MAX_DIM,
        quality: Int = DEFAULT_QUALITY,
    ): Loaded {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(resolver.openInputStream(uri), bounds)
            ?: throw IllegalStateException("Couldn't read the attached image.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("The attached file isn't a readable image.")
        }

        // Power-of-two sampling gets us near [maxDim] cheaply before the exact scale.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val sampled = decode(
            resolver.openInputStream(uri),
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalStateException("Couldn't decode the attached image.")

        val rotated = applyExifRotation(resolver.openInputStream(uri)?.use(::ExifInterface), sampled)
        val scaled = scaleToFit(rotated, maxDim)

        val out = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IllegalStateException("Couldn't encode the attached image.")
        }
        return Loaded(out.toByteArray(), "image/jpeg")
    }

    private fun decode(stream: InputStream?, options: BitmapFactory.Options): Bitmap? {
        if (stream == null) return null
        return stream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyExifRotation(exif: ExifInterface?, bitmap: Bitmap): Bitmap {
        val degrees = when (
            exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return if (rotated != bitmap) rotated else bitmap
    }

    private fun scaleToFit(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
