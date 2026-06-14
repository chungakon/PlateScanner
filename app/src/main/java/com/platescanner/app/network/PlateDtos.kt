package com.platescanner.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the M3 multimodal `/v1/chat/completions` endpoint and the
 * legacy `PlateRecognitionRequest` envelope (kept around so older call sites
 * and tests still compile against the type).
 *
 * Kept separate from the domain [com.platescanner.app.domain.PlateCandidate]
 * so the public contract stays stable even if the upstream JSON shape changes.
 */

// ---------- Chat completions request (track 2 — real wire format) ----------

@Serializable
data class ChatCompletionRequest(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<ChatMessage>,
    @SerialName("thinking")
    val thinking: ThinkingConfig? = null,
    @SerialName("temperature")
    val temperature: Double = 0.1,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int = 200,
)

@Serializable
data class ThinkingConfig(
    @SerialName("type")
    val type: String = "disabled",
)

@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: List<ChatContentPart>,
)

@Serializable
sealed class ChatContentPart {
    @Serializable
    @SerialName("text")
    data class Text(
        @SerialName("text") val text: String,
    ) : ChatContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImageUrl(
        @SerialName("image_url") val imageUrl: ImageUrlRef,
    ) : ChatContentPart()
}

@Serializable
data class ImageUrlRef(
    @SerialName("url") val url: String,
)

// ---------- Chat completions response ----------

@Serializable
data class ChatCompletionResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("choices") val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    @SerialName("index") val index: Int = 0,
    @SerialName("message") val message: ResponseMessage? = null,
)

@Serializable
data class ResponseMessage(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
)

// ---------- Domain envelopes / payload pieces ----------

/**
 * Legacy envelope from the track-1 stub DTO. Kept so external callers that
 * still reference it (e.g. an offline unit test) continue to compile.
 */
@Serializable
data class PlateRecognitionRequest(
    @SerialName("model")
    val model: String = "minimax-vl-01",
    @SerialName("image_base64")
    val imageBase64: String,
)

@Serializable
data class PlateRecognitionResponseDto(
    @SerialName("plates")
    val plates: List<PlateCandidateDto> = emptyList(),
)

@Serializable
data class PlateListEnvelope(
    @SerialName("plates")
    val plates: List<PlateCandidateDto> = emptyList(),
)

@Serializable
data class PlateCandidateDto(
    @SerialName("plate")
    val plate: String,
    @SerialName("confidence")
    val confidence: Float? = null,
    /**
     * Normalized 0..1 bounding box [left, top, right, bottom] in the source
     * image. Optional — the model may omit it (older revisions) and the UI
     * degrades gracefully when null. The four floats are kept as a List here
     * (matches what the LLM emits) and reshaped into a [BoundingBox] in the
     * domain layer.
     */
    @SerialName("bbox")
    val bbox: List<Float>? = null,
)
