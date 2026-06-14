package com.platescanner.app.domain

import kotlinx.serialization.Serializable

/**
 * A single plate candidate returned by the recognition API. Track 2 maps the
 * wire DTO to this domain model in the network layer.
 *
 * [bbox] is the plate's bounding box **in normalized image coordinates**
 * (0..1, top-left origin) as reported by the upstream M3 model. It is null
 * when the model did not return one — for legacy responses, parse failures,
 * or the model simply omitting the field. All UI rendering must handle null
 * (the overlay is suppressed, the plate number is still shown in the HUD).
 */
@Serializable
data class PlateCandidate(
    val plate: String,
    val confidence: Float? = null,
    val bbox: BoundingBox? = null,
)

/**
 * Rectangle in **normalized coordinates** (0..1, top-left origin) so the same
 * bbox is meaningful regardless of the source image's actual width/height.
 * The UI layer is responsible for projecting these onto whatever the camera
 * preview's display rect is.
 */
@Serializable
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        // Defensive: clamp silently instead of throwing from a deserializer.
        // We never want a malformed bbox to crash recognition — the UI just
        // skips drawing it.
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "BoundingBox coords must be normalized 0..1, got [$left,$top,$right,$bottom]"
        }
    }

    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isValid: Boolean get() = width > 0f && height > 0f
}
