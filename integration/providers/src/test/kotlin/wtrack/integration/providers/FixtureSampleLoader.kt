package wtrack.integration.providers

import wtrack.integration.NotificationEvent

@Suppress("UNCHECKED_CAST")
fun loadAnonymizedFixtureSamples(resourcePath: String): List<NotificationEvent> {
    val stream = requireNotNull(object {}.javaClass.getResourceAsStream(resourcePath)) {
        "Missing test resource: $resourcePath"
    }
    val rawJson = stream.bufferedReader().use { it.readText() }
    val root = SimpleJsonReader(rawJson).readValue() as Map<String, Any?>
    val samples = root["samples"] as List<Map<String, Any?>>
    return samples.map { item ->
        NotificationEvent(
            sourceId = item["sourceId"] as String,
            packageName = item["packageName"] as String,
            title = item["title"] as String?,
            text = item["text"] as String,
            bigText = item["bigText"] as String?,
            subText = item["subText"] as String?,
            channelId = item["channelId"] as String?,
            isGroupSummary = item["isGroupSummary"] as Boolean,
            isContentRedacted = item["isContentRedacted"] as Boolean,
            postedAtEpochMillis = (item["postedAtEpochMillis"] as Number).toLong(),
            notificationKey = item["notificationKeyHash"] as String?,
            notificationId = (item["notificationId"] as Number).toInt(),
            notificationTag = item["notificationTagAnonymized"] as String?,
            sourceEventReference = null,
        )
    }
}

private class SimpleJsonReader(private val src: String) {
    private var pos = 0

    fun readValue(): Any? {
        skipWhitespace()
        return when (val ch = peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f' -> readBoolean()
            'n' -> readNull()
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected JSON char '$ch' at $pos")
        }
    }

    private fun readObject(): Map<String, Any?> {
        expect('{')
        val map = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return map
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            map[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return map
                }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect('[')
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return list
        }
        while (true) {
            list += readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return list
                }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    val esc = src[pos++]
                    when (esc) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("Bad JSON escape \\$esc")
                    }
                }
                else -> sb.append(c)
            }
        }
        error("Unterminated string")
    }

    private fun readBoolean(): Boolean = if (src.startsWith("true", pos)) {
        pos += 4
        true
    } else {
        check(src.startsWith("false", pos))
        pos += 5
        false
    }

    private fun readNull(): Any? {
        check(src.startsWith("null", pos))
        pos += 4
        return null
    }

    private fun readNumber(): Long {
        val start = pos
        if (peek() == '-') pos++
        while (pos < src.length && src[pos].isDigit()) pos++
        return src.substring(start, pos).toLong()
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peek(): Char = src[pos]

    private fun expect(expected: Char) {
        check(src[pos] == expected) { "Expected '$expected' at $pos but saw '${src[pos]}'" }
        pos++
    }
}
