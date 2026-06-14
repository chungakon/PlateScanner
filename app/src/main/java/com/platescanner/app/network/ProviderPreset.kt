package com.platescanner.app.network

/**
 * Curated list of upstream LLM providers the app knows how to talk to. Each
 * preset bundles a [baseUrl] (already includes any provider-specific path
 * prefix) and a [modelId] (the chat-completions model name advertised by
 * that provider) so the user only has to paste an API key + tap "保存".
 *
 * Wire format is the OpenAI-compatible `POST /v1/chat/completions` schema:
 *
 *   {baseUrl}/v1/chat/completions
 *   Authorization: Bearer <apiKey>
 *   { "model": "<modelId>", "messages": [...], "temperature": ..., "max_tokens": ... }
 *
 * So far the three presets are MiniMax's own endpoint, OpenAI's public
 * endpoint (gpt-4o-mini), and Alibaba DashScope's OpenAI-compatible
 * `compatible-mode/v1` endpoint (qwen-vl-plus). Adding more presets is a
 * one-line change — append to [ALL] below.
 */
enum class ProviderPreset(
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val description: String,
) {
    MINIMAX(
        displayName = "MiniMax (自家)",
        baseUrl = "https://api.minimaxi.com",
        modelId = "MiniMax-M3",
        description = "MiniMax 多模态识别，支持图片 + 文本输入，点此自动填入 URL 和模型。",
    ),
    OPENAI(
        displayName = "OpenAI 兼容 (gpt-4o-mini)",
        baseUrl = "https://api.openai.com/v1",
        modelId = "gpt-4o-mini",
        description = "OpenAI 官方 gpt-4o-mini,标准 OpenAI Chat Completions 接口。",
    ),
    QWEN_VL(
        displayName = "通义千问 qwen-vl-plus",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        modelId = "qwen-vl-plus",
        description = "阿里云 DashScope OpenAI 兼容模式,模型 qwen-vl-plus 支持图片理解。",
    );

    companion object {
        /** UI-facing list rendered by the Settings screen. */
        val ALL: List<ProviderPreset> = entries.toList()

        /**
         * Resolve a preset by its display name (used when persisting the
         * user's last selection). Falls back to [MINIMAX] if no match.
         */
        fun fromDisplayName(name: String?): ProviderPreset =
            ALL.firstOrNull { it.displayName == name } ?: MINIMAX

        /**
         * Resolve a preset by its model id (used to auto-detect the active
         * preset from existing settings). Falls back to [MINIMAX].
         */
        fun fromModelId(modelId: String?): ProviderPreset =
            ALL.firstOrNull { it.modelId == modelId } ?: MINIMAX
    }
}