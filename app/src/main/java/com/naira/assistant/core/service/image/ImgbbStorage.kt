package com.naira.assistant.core.service.image

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import com.naira.assistant.core.config.IMGBB_API_KEY
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** One uploaded image — url = full size, thumb/medium = resized versions. */
@Serializable
data class ImgbbImage(
    @SerialName("id") val id: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("display_url") val displayUrl: String? = null,
    @SerialName("url_viewer") val viewerUrl: String? = null,
    @SerialName("delete_url") val deleteUrl: String? = null,
    @SerialName("expiration") val expiration: Long = 0,
    @SerialName("thumb") val thumb: ImgbbSize? = null,
    @SerialName("medium") val medium: ImgbbSize? = null,
) {
    val bestUrl: String? get() = url ?: displayUrl ?: thumb?.url
}

@Serializable
data class ImgbbSize(
    @SerialName("url") val url: String? = null,
    @SerialName("mime") val mime: String? = null,
    @SerialName("extension") val extension: String? = null,
)

@Serializable
internal data class ImgbbUploadResponse(
    @SerialName("data") val data: ImgbbImage? = null,
    @SerialName("success") val success: Boolean = false,
    @SerialName("status") val status: Int = 0,
)

@Serializable
internal data class ImgbbErrorResponse(@SerialName("error") val error: ImgbbError? = null,
                                       @SerialName("status_txt") val statusTxt: String? = null)

@Serializable
internal data class ImgbbError(@SerialName("message") val message: String? = null)

class ImgbbException(message: String, val code: String) : Exception(message)

class ImgbbStorage(
    private val apiKey: String = IMGBB_API_KEY,
    private val httpClient: HttpClient = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = IMAGE_UPLOAD_TIMEOUT_MS
            socketTimeoutMillis = IMAGE_UPLOAD_TIMEOUT_MS
        }
    },
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun upload(
        imageBytes: ByteArray,
        name: String? = null,
        expirationSeconds: Long? = null,
    ): ImgbbImage {
        if (apiKey.isBlank()) throw ImgbbException("ImgBB API key is missing.", "missing_api_key")
        if (imageBytes.isEmpty()) throw ImgbbException("Nothing to upload — the image is empty.", "empty_image")
        if (imageBytes.size > MAX_IMAGE_BYTES) throw ImgbbException("Image too large for ImgBB (max 32 MB).", "image_too_large")

        val form = Parameters.build {
            append("key", apiKey)
            append("image", Base64.encode(imageBytes))
            name?.takeIf { it.isNotBlank() }?.let { append("name", it.take(100)) }
            expirationSeconds
                ?.takeIf { it in MIN_EXPIRATION_SECONDS..MAX_EXPIRATION_SECONDS }
                ?.let { append("expiration", it.toString()) }
        }

        val text = httpClient.post(UPLOAD_URL) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(form))
        }.bodyAsText()

        val parsed = try {
            json.decodeFromString(ImgbbUploadResponse.serializer(), text)
        } catch (_: Exception) {
            throw ImgbbException("Couldn't upload the image. Please try again.", "bad_response")
        }

        val image = parsed.data
        if (!parsed.success || image == null || image.bestUrl == null) {
            throw ImgbbException(extractError(text), "upload_failed")
        }
        return image
    }

    /** Never-throwing variant — null means the upload failed. */
    suspend fun uploadOrNull(
        imageBytes: ByteArray,
        name: String? = null,
        expirationSeconds: Long? = null,
    ): ImgbbImage? = try {
        upload(imageBytes, name, expirationSeconds)
    } catch (_: Exception) {
        null
    }

    fun close() { httpClient.close() }

    private fun extractError(rawBody: String): String {
        val apiMessage = try {
            json.decodeFromString(ImgbbErrorResponse.serializer(), rawBody).error?.message
        } catch (_: Exception) { null }
        return apiMessage?.takeIf { it.isNotBlank() }
            ?: "Couldn't upload the image. Please try again."
    }

    companion object {
        const val UPLOAD_URL = "https://api.imgbb.com/1/upload"
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
        const val MIN_EXPIRATION_SECONDS = 60L
        const val MAX_EXPIRATION_SECONDS = 15_552_000L
        const val IMAGE_UPLOAD_TIMEOUT_MS = 60_000L
    }
}