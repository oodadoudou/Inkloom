package app.inkloom.document

import java.net.URI
import java.util.Locale

object LinkTarget {
    private const val MAX_LENGTH = 2_048
    private val allowedSchemes = setOf("http", "https", "mailto", "tel")
    private val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val phoneNumber = Regex("^\\+?[0-9(). -]{3,32}$")

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH || trimmed.any(Char::isISOControl)) return null
        val candidate = if (explicitScheme.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        if (candidate.substringBefore(':').equals("tel", ignoreCase = true)) {
            return candidate.substringAfter(':').trim().takeIf { phoneNumber.matches(it) }?.let { "tel:$it" }
        }
        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)?.takeIf { it in allowedSchemes } ?: return null
        return when (scheme) {
            "http", "https" -> {
                if (parsed.host.isNullOrBlank() || parsed.rawAuthority.any(Char::isWhitespace)) null
                else "$scheme:${parsed.toASCIIString().substringAfter(':')}"
            }
            "mailto" -> parsed.rawSchemeSpecificPart.takeIf { address ->
                address.substringBefore('?').let { it.contains('@') && !it.startsWith('@') && !it.endsWith('@') }
            }?.let { "$scheme:$it" }
            "tel" -> null
            else -> null
        }
    }

    fun displayDestination(raw: String, maximumLength: Int = 72): String {
        val normalized = normalize(raw) ?: return raw.trim().take(maximumLength.coerceAtLeast(1))
        val visible = normalized.removePrefix("https://").removePrefix("http://")
        val limit = maximumLength.coerceAtLeast(1)
        return if (visible.length <= limit) visible else visible.take((limit - 1).coerceAtLeast(0)) + "…"
    }
}
