package app.olauncher.data

private val schemeRegex = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
private val domainRegex = Regex("""^([\w-]+\.)+[a-z][\w-]*(:\d+)?([/?#]\S*)?$""", RegexOption.IGNORE_CASE)

// Returns a URL if the search text is a link (e.g. "zeit.de" or "https://…"), otherwise null
fun linkUrl(text: String): String? {
    val trimmed = text.trim()
    return when {
        schemeRegex.matches(trimmed) -> trimmed
        domainRegex.matches(trimmed) -> "https://$trimmed"
        else -> null
    }
}
