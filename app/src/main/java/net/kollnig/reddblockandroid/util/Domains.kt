package net.kollnig.reddblockandroid.util

private val domainPattern = Regex("^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}$")

/**
 * Turns user input such as "https://www.Reddit.com/r/foo" into a bare domain
 * ("reddit.com"), or returns null when the input is not a valid domain.
 */
fun normaliseDomain(input: String): String? {
    var domain = input.trim().lowercase()
    domain = domain.removePrefix("https://").removePrefix("http://")
    domain = domain.removePrefix("www.")
    domain = domain.split('/', '?', '#', ':').first().trimEnd('.')
    return domain.takeIf { domainPattern.matches(it) }
}
