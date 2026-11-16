package org.quackulous.data

import java.io.File

/** Names a word list Quackle knows, by the identity hash its dictionary files carry; see lexica/copyrights.txt. */
object WordListRecognizer {
    fun name(dataDirectory: File, hash: String): String? {
        val file = File(dataDirectory, "lexica/copyrights.txt")
        if (!file.exists() || hash.length < 8) return null
        return name(file.readLines(), hash)
    }

    internal fun name(lines: List<String>, hash: String): String? {
        val line = lines.firstOrNull { it.length > 9 && it[8] == ':' && it.startsWith(hash.take(8)) } ?: return null
        // "<hash>:<name>, <licence>[:<logo>]"
        val text = line.substring(9)
        val withoutLogo = if (text.contains(':')) text.substringBeforeLast(':') else text
        return withoutLogo.substringBefore(',').trim()
    }
}
