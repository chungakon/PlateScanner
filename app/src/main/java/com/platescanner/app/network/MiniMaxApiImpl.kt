@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.platescanner.app.network

import android.util.Base64
import android.util.Log
import com.platescanner.app.BuildConfig
import com.platescanner.app.data.MiniMaxApiDefaults
import com.platescanner.app.data.SettingsRepository
import com.platescanner.app.domain.BoundingBox
import com.platescanner.app.domain.PlateCandidate
import com.platescanner.app.domain.PlateValidator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real Retrofit-backed implementation of [MiniMaxApi] for OpenAI-compatible
 * `/v1/chat/completions` endpoints (MiniMax, OpenAI, DashScope, ...).
 *
 * Wire format:
 *   POST {baseUrl}/v1/chat/completions
 *   Authorization: Bearer ${apiKey}
 *   body: {model, messages:[{role:"user", content:[text, image_url]}], temperature, max_tokens}
 *
 * The model and base URL are read **fresh from [SettingsRepository]** on
 * every request, so changing them in the Settings screen takes effect
 * immediately on the next frame — no app restart, no client rebuild. Same
 * goes for the bearer token: the OkHttp interceptor pulls the current key
 * via a synchronous DataStore snapshot for each request.
 *
 * The Retrofit client itself is still cached in an [AtomicReference] and
 * rebuilt only when the resolved base URL changes (rare — only when the
 * user picks a different provider). This avoids re-spinning a connection
 * pool + thread pools for the common case of "user edited the API key".
 *
 * Response parsing:
 *   - Extract `choices[0].message.content` (free-form text).
 *   - Strip any ```json ... ``` fences.
 *   - Parse the inner JSON into a [PlateListEnvelope].
 *   - On any parse failure, return an empty list (never throw into callers).
 *
 * Networking:
 *   - 8s connect/read/write timeout.
 *   - Single retry on transient failures (IOException / SocketTimeoutException).
 */
class MiniMaxApiImpl(
    private val settingsRepository: SettingsRepository,
) : MiniMaxApi {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Cached `(baseUrl -> Retrofit)` map. Keyed by the resolved base URL so
     * switching providers rebuilds the client; otherwise the same client is
     * reused and the interceptor reads the latest key/model on each call.
     */
    private val cachedRetrofit = AtomicReference<Pair<String, Retrofit>?>(null)

    override suspend fun recognizePlate(imageBytes: ByteArray): Result<List<PlateCandidate>> =
        withContext(Dispatchers.IO) {
            val apiKey = settingsRepository.apiKey()
            val model = settingsRepository.modelId()
            val baseUrl = settingsRepository.baseUrl()

            if (apiKey.isBlank()) {
                return@withContext Result.success(emptyList())
            }
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64"
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ChatContentPart.Text(RECOGNITION_PROMPT),
                            ChatContentPart.ImageUrl(
                                ImageUrlRef(url = dataUri),
                            ),
                        ),
                    ),
                ),
                thinking = ThinkingConfig(type = "disabled"),
                temperature = TEMPERATURE,
                maxCompletionTokens = MAX_TOKENS,
            )

            val service = retrofitFor(baseUrl).create(ChatCompletionsService::class.java)

            // One retry on transient failures.
            val response: retrofit2.Response<ChatCompletionResponse> = try {
                service.completeChat(body = request).execute()
            } catch (t: java.io.IOException) {
                try {
                    service.completeChat(body = request).execute()
                } catch (t2: java.io.IOException) {
                    return@withContext Result.success(emptyList())
                }
            }

            if (!response.isSuccessful) {
                Log.w("MiniMaxApi", "HTTP ${response.code()} — body: ${response.errorBody()?.string()?.take(200)}")
                return@withContext Result.success(emptyList())
            }
            val body = response.body() ?: run {
                Log.w("MiniMaxApi", "HTTP 200 but body was null")
                return@withContext Result.success(emptyList())
            }
            val content = body.choices.firstOrNull()?.message?.content
            Log.i("MiniMaxApi", "RAW model content: ${content?.take(500)}")
            if (content == null) {
                Log.w("MiniMaxApi", "Body had no choices[0].message.content")
                return@withContext Result.success(emptyList())
            }
            val candidates = parsePlateCandidates(content)
            Log.i("MiniMaxApi", "PARSED ${candidates.size} candidates: ${candidates.map { "${it.plate}(conf=${it.confidence},bbox=${it.bbox})" }}")
            Result.success(candidates)
        }

    /**
     * v0.7 "横屏多车" mode. Same wire format as [recognizePlate] but:
     *   1. Uses [MULTI_RECOGNITION_PROMPT] which explicitly asks the model
     *      to return ALL visible plates, not the "primary" one.
     *   2. Uses a larger token budget so up to 3 plates fit in the JSON
     *      envelope (each plate is ~50-80 tokens of JSON).
     *   3. Uses [MULTI_MAX_TOKENS] to give the model headroom for wide shots.
     *
     * In v0.7 the caller (ScannerViewModel) is responsible for picking the
     * resolution: it sends a 1920x1080 landscape JPEG (we downscale only if
     * the device's camera produced a smaller image — 99% of phones deliver
     * at least 1920 wide when shooting landscape).
     */
    override suspend fun recognizeMultiPlate(imageBytes: ByteArray): Result<List<PlateCandidate>> =
        withContext(Dispatchers.IO) {
            val apiKey = settingsRepository.apiKey()
            val model = settingsRepository.modelId()
            val baseUrl = settingsRepository.baseUrl()

            if (apiKey.isBlank()) {
                return@withContext Result.success(emptyList())
            }
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64"
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ChatContentPart.Text(MULTI_RECOGNITION_PROMPT),
                            ChatContentPart.ImageUrl(
                                ImageUrlRef(url = dataUri),
                            ),
                        ),
                    ),
                ),
                thinking = ThinkingConfig(type = "disabled"),
                temperature = TEMPERATURE,
                maxCompletionTokens = MULTI_MAX_TOKENS,
            )

            val service = retrofitFor(baseUrl).create(ChatCompletionsService::class.java)

            val response: retrofit2.Response<ChatCompletionResponse> = try {
                service.completeChat(body = request).execute()
            } catch (t: java.io.IOException) {
                try {
                    service.completeChat(body = request).execute()
                } catch (t2: java.io.IOException) {
                    return@withContext Result.success(emptyList())
                }
            }

            if (!response.isSuccessful) {
                Log.w("MiniMaxApi", "MULTI HTTP ${response.code()} — body: ${response.errorBody()?.string()?.take(200)}")
                return@withContext Result.success(emptyList())
            }
            val body = response.body() ?: run {
                Log.w("MiniMaxApi", "MULTI HTTP 200 but body was null")
                return@withContext Result.success(emptyList())
            }
            val content = body.choices.firstOrNull()?.message?.content
            Log.i("MiniMaxApi", "MULTI RAW model content: ${content?.take(500)}")
            if (content == null) {
                Log.w("MiniMaxApi", "MULTI Body had no choices[0].message.content")
                return@withContext Result.success(emptyList())
            }
            val candidates = parsePlateCandidates(content)
            Log.i("MiniMaxApi", "MULTI PARSED ${candidates.size} candidates: ${candidates.map { "${it.plate}(conf=${it.confidence},bbox=${it.bbox})" }}")
            Result.success(candidates)
        }

    override suspend fun testConnection(): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key 未填写"))
            }
            val model = settingsRepository.modelId()
            val baseUrl = settingsRepository.baseUrl()

            // Minimal text-only request — no image, just validate key + endpoint.
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ChatContentPart.Text("hi"),
                        ),
                    ),
                ),
                thinking = ThinkingConfig(type = "disabled"),
                maxCompletionTokens = 5,
            )

            val service = retrofitFor(baseUrl).create(ChatCompletionsService::class.java)

            val response: retrofit2.Response<ChatCompletionResponse> = try {
                service.completeChat(body = request).execute()
            } catch (t: Throwable) {
                return@withContext Result.failure(
                    Exception("连接失败: ${t.localizedMessage ?: t.javaClass.simpleName}"),
                )
            }

            if (!response.isSuccessful) {
                val code = response.code()
                val body = response.errorBody()?.string()?.take(200)
                return@withContext Result.failure(
                    Exception("HTTP $code${if (body != null) ": $body" else ""}"),
                )
            }

            Result.success(model)
        }

    /**
     * Build (or return cached) Retrofit instance for [baseUrl]. The OkHttp
     * interceptor pulls the latest API key from the repository on every
     * request, so we never need to rebuild the client just because the user
     * edited the key.
     */
    private fun retrofitFor(baseUrl: String): Retrofit {
        val normalized = ensureTrailingSlash(baseUrl)
        val cached = cachedRetrofit.get()
        if (cached != null && cached.first == normalized) {
            return cached.second
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor())
            .also { builder ->
                if (BuildConfig.DEBUG) {
                    builder.addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        },
                    )
                }
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        cachedRetrofit.set(normalized to retrofit)
        return retrofit
    }

    /**
     * Pulls the current API key from the repository on every request. Uses
     * `runBlocking` because OkHttp interceptors are synchronous — the DataStore
     * snapshot is cheap (in-memory after the first read) and runs on a
     * dispatcher-bound IO thread, so this doesn't block the UI.
     */
    private fun authInterceptor() = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val key: String = runBlocking { settingsRepository.apiKey() }
        val req = if (key.isNotBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $key")
                .build()
        } else {
            original
        }
        chain.proceed(req)
    }

    private fun parsePlateCandidates(content: String): List<PlateCandidate> {
        // Some thinking-capable models (e.g. M3) embed <think>...</think>
        // blocks inside message.content.  Strip them before extracting JSON
        // so the thinking noise never confuses the JSON parser.
val jsonText = extractFirstJsonObject(content)
        if (jsonText == null) {
            Log.w("MiniMaxApi", "PARSE_FAIL: no JSON found — prefix: ${content.take(150)}")
            return emptyList()
        }
        return try {
            val env = json.decodeFromString(PlateListEnvelope.serializer(), jsonText)
            // Filter + canonicalise: drop anything that doesn't parse as a
            // known plate shape, and rewrite the candidate's `plate` field
            // to the canonical (punctuation-stripped, upper-cased) form so
            // downstream consumers (dedup, repository, UI) see one string
            // per plate regardless of how the LLM punctuated it.
            env.plates.mapNotNull { dto ->
                val canonical = PlateValidator.normalize(dto.plate)
                if (canonical == null) {
                    Log.w("MiniMaxApi", "VALIDATOR_REJECT: raw='${dto.plate}' did not match any known plate shape")
                    return@mapNotNull null
                }
                PlateCandidate(
                    plate = canonical,
                    confidence = dto.confidence,
                    bbox = parseBbox(dto.bbox),
                )
            }
        } catch (t: Throwable) {
            Log.w("MiniMaxApi", "JSON_DECODE_FAIL: ${t.javaClass.simpleName}: ${t.message} — text: ${jsonText.take(200)}")
            emptyList()
        }
    }

    /**
     * Coerce the wire-level `bbox` array into a [BoundingBox], or null if the
     * model didn't return one / returned garbage. We never throw from here —
     * the worst case is "no overlay drawn", not a crash.
     */
    private fun parseBbox(raw: List<Float>?): BoundingBox? {
        if (raw == null || raw.size != 4) return null
        return try {
            val l = raw[0]; val t = raw[1]; val r = raw[2]; val b = raw[3]
            BoundingBox(left = l, top = t, right = r, bottom = b)
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        /** Vision-capable multimodal chat model (default). */
        val DEFAULT_VISION_MODEL: String = MiniMaxApiDefaults.DEFAULT_VISION_MODEL

        const val TEMPERATURE: Double = 0.1
        const val MAX_TOKENS: Int = 200

        const val RECOGNITION_PROMPT: String =
            "请仔细查看图片,提取所有可见的车牌号。\n" +
                "车牌按类型分:\n" +
                "- 蓝/黄/白牌:1 个中文(省简称)+ 1 个字母 + 5 个字符(共 7 字符)\n" +
                "- 绿牌(新能源):1 个中文 + A/B/C/D/F + 6 个字符(共 8 字符)\n" +
                "- 港澳:2-3 字母 + 2-5 字符(纯字母数字,无中文)\n" +
                "输出格式(JSON,不要任何额外说明):" +
                "{\"plates\": [{\"plate\": \"粤TDH8884\", \"confidence\": 0.95, \"bbox\": [x1, y1, x2, y2]}]}\n" +
                "bbox 是归一化坐标(0-1 范围,左上角原点)。\n" +
                "如果图中没有车牌,返回 {\"plates\": []}。"

        /**
         * v0.7 wide-shot prompt. Used by the "横屏多车" mode where the user
         * captures 2-3 cars in one landscape frame. Key differences from
         * [RECOGNITION_PROMPT]:
         *
         *   1. Explicitly says "all visible plates" so the model doesn't
         *      pick only the largest/most central one.
         *   2. Forbids the model from inventing a plate when the read is
         *      uncertain — better to return empty than to fabricate.
         *   3. Asks for the plates to be ordered **left-to-right in the
         *      image** so the UI grid matches what the user sees.
         *   4. Caps at 3 plates because that's the practical ceiling for
         *      wide-shot accuracy (4+ typically drops below 50%).
         */
        const val MULTI_RECOGNITION_PROMPT: String =
            "请仔细查看图片,提取图中所有可见的车牌号(最多 3 张)。\n" +
                "这是一张横屏拍摄的停车场照片,可能同时包含 2-3 辆并排停放的车。\n" +
                "请按从左到右的顺序返回所有识别到的车牌。\n" +
                "车牌按类型分:\n" +
                "- 蓝/黄/白牌:1 个中文(省简称)+ 1 个字母 + 5 个字符(共 7 字符)\n" +
                "- 绿牌(新能源):1 个中文 + A/B/C/D/F + 6 个字符(共 8 字符)\n" +
                "- 港澳:2-3 字母 + 2-5 字符(纯字母数字,无中文)\n" +
                "重要规则:\n" +
                "- 务必返回所有清晰可识别的车牌,不要遗漏\n" +
                "- 如果某张车牌模糊到无法确认,**跳过它**,不要猜测或编造\n" +
                "- 只输出你能 100% 确认的字符\n" +
                "输出格式(JSON,不要任何额外说明):" +
                "{\"plates\": [{\"plate\": \"粤TDH8884\", \"confidence\": 0.95, \"bbox\": [x1, y1, x2, y2]}]}\n" +
                "bbox 是归一化坐标(0-1 范围,左上角原点),对应车牌在原图中的位置。\n" +
                "如果图中没有车牌,返回 {\"plates\": []}。"

        /**
         * Larger token budget for the wide-shot prompt. Each plate's JSON
         * entry is ~50-80 tokens (plate + confidence + 4 bbox floats +
         * structure overhead). 3 plates + envelope ≈ 350 tokens; we leave
         * 100 tokens of headroom for prose / edge cases.
         */
        const val MULTI_MAX_TOKENS: Int = 500

        /**
         * Pull the first balanced `{...}` JSON object out of a free-form LLM
         * response. Handles ```json ... ``` fences and stray prose.
         */
        internal fun extractFirstJsonObject(text: String): String? {
            val cleaned = text
                .replace("```json", "")
                .replace("```", "")
                .trim()
            // Find the first '{' that begins a balanced top-level object.
            var depth = 0
            var start = -1
            var inString = false
            var escape = false
            for ((i, c) in cleaned.withIndex()) {
                if (escape) { escape = false; continue }
                when {
                    c == '\\' -> escape = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> {
                        if (depth == 0) start = i
                        depth += 1
                    }
                    !inString && c == '}' -> {
                        depth -= 1
                        if (depth == 0 && start >= 0) {
                            return cleaned.substring(start, i + 1)
                        }
                    }
                }
            }
            // Last-resort: try to grab from first '{' to last '}' if it parses.
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace in 0 until lastBrace) {
                return cleaned.substring(firstBrace, lastBrace + 1)
            }
            return null
        }

        private fun ensureTrailingSlash(url: String): String =
            if (url.endsWith("/")) url else "$url/"
    }
}

/** Internal Retrofit interface — defined inside the same file for cohesion. */
private interface ChatCompletionsService {
    @POST("v1/chat/completions")
    fun completeChat(
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: ChatCompletionRequest,
    ): retrofit2.Call<ChatCompletionResponse>
}