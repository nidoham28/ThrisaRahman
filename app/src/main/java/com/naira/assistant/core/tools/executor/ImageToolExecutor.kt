package com.naira.assistant.core.tools.executor

import android.content.Context
import com.naira.assistant.core.service.image.GeminiImageGenerationService
import com.naira.assistant.core.service.image.InlineImage
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ImageToolExecutor {
    private val supportedRatios = setOf(
        "auto", "1:1", "3:2", "2:3", "4:3", "3:4", "4:5", "5:4", "16:9", "9:16",
        "21:9", "1:4", "4:1", "1:8", "8:1",
    )
    private val supportedImageSizes = setOf("512", "1K", "2K", "4K")

    /**
     * Runs generate_image. [attachedImage] is the image the user attached to the
     * current message, when any; the model requests it by setting
     * `edit_attached_image = true` to get an image + text → image edit instead
     * of a fresh generation.
     */
    suspend fun executeGenerateImage(
        call: FunctionCall,
        context: Context,
        attachedImage: InlineImage.Loaded? = null,
    ): JsonObject {
        val prompt = jsonStringArg(call, "prompt")?.trim()
            ?: return error("Describe the image you want to create.")
        val requestedRatio = jsonStringArg(call, "aspect_ratio")?.trim() ?: "auto"
        if (requestedRatio !in supportedRatios) {
            return error("Choose a supported aspect ratio or omit it.")
        }
        val imageSize = jsonStringArg(call, "image_size")?.trim() ?: "1K"
        if (imageSize !in supportedImageSizes) {
            return error("Choose 512, 1K, 2K, or 4K for image size.")
        }

        val editAttached = call.args["edit_attached_image"]?.jsonPrimitive?.booleanOrNull == true
        if (editAttached && attachedImage == null) {
            return error("No image is attached to this message, so there is nothing to edit. Create a new image instead.")
        }
        val reference = attachedImage.takeIf { editAttached }

        val image = GeminiImageGenerationService.generate(context, prompt, requestedRatio, imageSize, reference)
        return buildJsonObject {
            put("success", true)
            put("action", if (editAttached) "image_edited" else "image_generated")
            put("image_uri", image.uri)
            put("mime_type", image.mimeType)
            put("image_size", imageSize)
            put("saved_to", image.savedTo)
            put(
                "message",
                if (editAttached) "The attached image was edited and saved. It is ready to view in the conversation."
                else "Image generated and saved. It is ready to view in the conversation."
            )
        }
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }
}
