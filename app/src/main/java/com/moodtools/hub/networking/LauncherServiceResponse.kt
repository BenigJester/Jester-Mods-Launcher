package com.moodtools.hub.networking

import org.json.JSONException
import org.json.JSONObject

internal const val MAX_LAUNCHER_RESPONSE_CHARS = 1024 * 1024

/** Never retain an HTML challenge page or a parser exception containing response secrets. */
internal fun parseLauncherServiceResponse(status: Int, contentType: String?, text: String): JSONObject {
    fun invalid(reason: String): Nothing = throw LauncherServiceException(
        "UNEXPECTED_SERVICE_RESPONSE",
        "Launcher service HTTP $status: $reason. Expected a JSON object; retry later."
    )
    if (text.length > MAX_LAUNCHER_RESPONSE_CHARS) invalid("response exceeded the size limit")
    val body = text.trim().removePrefix("\uFEFF").trimStart()
    if (contentType?.substringBefore(';')?.trim()?.equals("text/html", ignoreCase = true) == true ||
        body.startsWith("<")) invalid("received an HTML page instead of an API response")
    if (!body.startsWith("{")) invalid(if (body.isEmpty()) "empty response" else "non-object response")
    val response = try {
        JSONObject(body)
    } catch (_: JSONException) {
        invalid("malformed JSON response")
    }
    // Preserve structured service denials, but never accept success on a failed HTTP request.
    if (status !in 200..299 && response.optBoolean("ok", true)) {
        throw LauncherServiceException("SERVICE_HTTP_ERROR", "Launcher service HTTP $status: request failed")
    }
    return response
}
