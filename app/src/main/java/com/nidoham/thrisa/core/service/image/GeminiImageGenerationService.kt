package com.nidoham.thrisa.core.service.image

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.nidoham.thrisa.core.config.API_KEYS
import com.nidoham.thrisa.core.config.isApiKeyVaultUnlocked
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.Locale

data class GeneratedImage(val uri: String, val mimeType: String, val savedTo: String)

/**
 * Generates (or edits) an image with Gemini and saves it to Pictures/Thrisa
 * when shared storage is available.
 *
 * When [generate] receives a [referenceImage], the request becomes
 * image + text → image: the model restyles, edits or combines the supplied
 * picture per the prompt instead of inventing one from scratch.
 */
object GeminiImageGenerationService {
    private const val MODEL = "gemini-3.1-flash-image"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1/models"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    // Keep the engine and connection pool warm across image requests.
    private val httpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }

    suspend fun generate(
        context: Context,
        prompt: String,
        aspectRatio: String,
        imageSize: String = "1K",
        referenceImage: InlineImage.Loaded? = null,
    ): GeneratedImage {
        val apiKey = API_KEYS(context.applicationContext).trim()
        require(apiKey.isNotBlank()) {
            if (isApiKeyVaultUnlocked()) {
                "Add a Gemini API key in Settings → AI Providers → Credentials first."
            } else {
                "Unlock your provider keys in Settings → AI Providers first."
            }
        }
        require(prompt.isNotBlank()) { "Describe the image you want to create." }

        val response = httpClient.post("$BASE_URL/$MODEL:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        putJsonArray("parts") {
                            if (referenceImage != null) {
                                add(buildJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", referenceImage.mimeType)
                                        put("data", referenceImage.base64())
                                    }
                                })
                            }
                            add(buildJsonObject { put("text", prompt) })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("IMAGE") }
                    putJsonObject("responseFormat") {
                        putJsonObject("image") {
                            if (aspectRatio != "auto") put("aspectRatio", aspectRatio)
                            put("imageSize", imageSize)
                        }
                    }
                }
            })
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Gemini image generation failed (${response.status.value}): ${extractApiError(body)}")
        }
        val root = json.parseToJsonElement(body).jsonObject
        val imagePart = root["candidates"]?.let { candidates ->
            candidates.jsonArray.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstNotNullOfOrNull { part ->
                    part.jsonObject["inlineData"]?.jsonObject
                }
        } ?: throw IllegalStateException("Gemini returned no image. Try a different prompt.")
        val mimeType = imagePart["mimeType"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("image/") }
            ?: throw IllegalStateException("Gemini returned an unsupported image format.")
        val imageData = imagePart["data"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Gemini returned empty image data.")
        val bytes = Base64.decode(imageData, Base64.DEFAULT)
        require(bytes.isNotEmpty()) { "Gemini returned an empty image." }
        return saveImage(context.applicationContext, bytes, mimeType)
    }

    private suspend fun saveImage(context: Context, bytes: ByteArray, mimeType: String): GeneratedImage =
        withContext(Dispatchers.IO) {
            val extension = mimeType.substringAfter('/').lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "png"
            val name = "thrisa_${System.currentTimeMillis()}.$extension"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Thrisa")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Couldn't save the image to Pictures.")
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Couldn't open the generated image file.")
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                    GeneratedImage(uri.toString(), mimeType, "Pictures/Thrisa")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "Thrisa")
                check(directory.exists() || directory.mkdirs()) { "Couldn't create the image folder." }
                val file = File(directory, name)
                file.writeBytes(bytes)
                GeneratedImage(Uri.fromFile(file).toString(), mimeType, "Thrisa app storage")
            }
        }

    private fun extractApiError(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }?.take(300) ?: "Check your Gemini API key and try again."
}
