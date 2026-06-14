package com.platescanner.app.domain

/**
 * Pure-Kotlin validator for Chinese mainland license plates (plus HK / Macau).
 *
 * No Android dependencies — safe to unit-test on the JVM and safe to call
 * from any layer (network, repository, VM).
 *
 * Two distinct concerns:
 *
 *  - [normalize] canonicalises a free-form string the OCR model might emit
 *    (e.g. "粤 T·DH8884") into the canonical "粤TDH8884" form. Returns null
 *    when the cleaned string does not parse as a known plate shape — that
 *    is the cleanest signal for the caller to drop the candidate.
 *
 *  - [isValid] / [typeOf] are the pure shape check. Use these to decide
 *    "should I save this?" and "what colour should the box be?".
 *
 * The three supported shapes:
 *
 *  | Type              | Example      | Regex summary                                |
 *  |-------------------|--------------|----------------------------------------------|
 *  | Blue/Yellow/White | 粤TDH8884    | 1 中 + 1 字母 + 5 字符(允许警/挂/学/港/澳后缀)|
 *  | Green (new-energy)| 京AD12345    | 1 中 + A-D/F + 6 字符(共 8 字符)             |
 *  | HK / Macau        | AB1234       | 2-3 字母 + 2-5 字母数字(纯英数,无中文)      |
 */
object PlateValidator {

    /**
     * Strip the punctuation LLMs love to insert between characters (the
     * middle dot "·", full-stop ".", space " ", hyphen "-", underscore "_")
     * and uppercase everything that survived.
     */
    private val PUNCT_TO_STRIP = Regex("[·.\\s\\-_]")

    /**
     * Province / municipal / autonomous-region single-character prefix
     * (省简称). Includes the special "使" (diplomatic) and "领" (consular)
     * characters used on diplomatic plates.
     */
    private const val PROVINCE_PREFIX =
        "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领"

    /**
     * Blue / yellow / white plates.
     *
     * Three legal shapes (all start with 1 中文 + 1 字母):
     *  - 7 chars total: 1 + 1 + 5 letters/digits.        (most common, e.g. 粤A12345)
     *  - 8 chars with Chinese suffix: 1 + 1 + 4 + 挂/学/警/港/澳. (police / army / learn)
     *  - 8 chars pure alphanumeric: 1 + 1 + 6 letters/digits.      (newer 6-tail variant, e.g. 粤TDH8884)
     *
     * The third shape was added after a real OCR pass: vision models (M3, GPT-4o)
     * consistently emit the 6-tail form on plates where the body is 6 characters
     * instead of 5 — both are valid under the GB-7258 spec.
     */
    private val BLUE_YELLOW_WHITE_REGEX = Regex(
        "^[$PROVINCE_PREFIX][A-Z]([A-Z0-9]{5}|[A-Z0-9]{6}|[A-Z0-9]{4}[挂学警港澳])$"
    )

    /**
     * Green (new-energy) plates — 8 characters; second char must be A-D or F
     * (E is reserved for non-pure-electric microvans and not in our match set).
     */
    private val GREEN_NEW_ENERGY_REGEX = Regex(
        "^[$PROVINCE_PREFIX][A-DF][A-Z0-9]{6}$"
    )

    /**
     * HK / Macau plates — pure ASCII, no Chinese. 2-3 leading letters
     * followed by 2-5 alphanumeric characters. We don't try to enforce
     * specific HK/MC shapes (they have ~10 distinct formats); the loose
     * "2-3 letters + 2-5 chars" rule is good enough to keep garbage out
     * of the database.
     */
    private val HK_MACAU_REGEX = Regex(
        "^[A-Z]{2,3}[A-Z0-9]{2,5}$"
    )

    enum class PlateType {
        /** 蓝/黄/白牌 — the most common shape. */
        BLUE_YELLOW_WHITE,

        /** 绿牌 (new-energy vehicle). */
        GREEN_NEW_ENERGY,

        /** 港澳牌 — pure alphanumeric, no Chinese. */
        HK_MACAU,
    }

    /**
     * Canonicalise [raw] and run the shape check.
     *
     *  - Punctuation ("·", ".", " ", "-", "_") is stripped.
     *  - Latin letters are upper-cased; Chinese characters are left alone.
     *  - The cleaned string is then matched against the three plate shapes.
     *
     * @return the canonical plate string on success, or null if the input
     *   could not be parsed as a known plate.
     */
    fun normalize(raw: String): String? {
        if (raw.isEmpty()) return null
        val cleaned = PUNCT_TO_STRIP.replace(raw, "").uppercase()
        if (cleaned.isEmpty()) return null
        return if (isValid(cleaned)) cleaned else null
    }

    /**
     * Pure shape check on an already-canonical plate string. Use [normalize]
     * if the input may contain punctuation / lower-case.
     */
    fun isValid(plate: String): Boolean = typeOf(plate) != null

    /**
     * Identify which family the plate belongs to. Returns null for any
     * string that doesn't fit a known shape.
     *
     * Order matters: the new-energy shape (8 chars, A-D/F in slot 2) is a
     * strict subset of the new 6-tail blue shape (also 8 chars, any letter),
     * so we have to test green first or `京AD12345` would be mis-classified
     * as a blue plate.
     */
    fun typeOf(plate: String): PlateType? {
        if (plate.isEmpty()) return null
        return when {
            GREEN_NEW_ENERGY_REGEX.matches(plate) -> PlateType.GREEN_NEW_ENERGY
            BLUE_YELLOW_WHITE_REGEX.matches(plate) -> PlateType.BLUE_YELLOW_WHITE
            HK_MACAU_REGEX.matches(plate) -> PlateType.HK_MACAU
            else -> null
        }
    }
}
