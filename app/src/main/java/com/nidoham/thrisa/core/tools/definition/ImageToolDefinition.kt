package com.nidoham.thrisa.core.tools.definition

import com.nidoham.thrisa.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun generateImageTool() = ToolDefinition(
    name = "generate_image",
    description = "Create an original image from the user's description using Gemini image generation, save it to Pictures/Thrisa, " +
            "and return the image for display in chat. Also EDIT an image the user attached to their current message: " +
            "set edit_attached_image = true and describe the change (restyle, replace elements, combine, restore, etc.). " +
            "Use this tool only when the user asks to generate, draw, create, edit, or illustrate an image. " +
            "Do not claim an image was created unless this tool succeeds.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "Detailed visual description of the image to create, or of the change to make to the attached image, including subject, style, composition, lighting, colors, and any requested text.")
            }
            putJsonObject("edit_attached_image") {
                put("type", "boolean")
                put("description", "True to transform the image the user attached to THIS message (style transfer, edit, upscale look, background change, etc.) instead of creating a new image from scratch. False (default) generates a brand-new image.")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Optional composition ratio. Use auto when unspecified.")
                putJsonArray("enum") {
                    add("auto")
                    add("1:1")
                    add("3:2")
                    add("2:3")
                    add("4:3")
                    add("3:4")
                    add("4:5")
                    add("5:4")
                    add("16:9")
                    add("9:16")
                    add("21:9")
                    add("1:4")
                    add("4:1")
                    add("1:8")
                    add("8:1")
                }
            }
            putJsonObject("image_size") {
                put("type", "string")
                put("description", "Optional output resolution. Use 1K by default; choose 2K or 4K for higher detail, or 512 for a smaller image.")
                putJsonArray("enum") {
                    add("512")
                    add("1K")
                    add("2K")
                    add("4K")
                }
            }
        }
        putJsonArray("required") { add("prompt") }
    }
)
