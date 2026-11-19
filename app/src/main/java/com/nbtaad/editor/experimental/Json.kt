package com.nbtaad.editor.experimental

/**
 * A small JSON reader (objects become Map, arrays List, numbers Long or Double), so GitHub's answers can be read and
 * tested without Android's org.json.
 */
object Json {
    class JsonException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Unexpected text after the JSON at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (atEnd()) throw JsonException("Unexpected end of the JSON")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("Unexpected '$c' at $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Unexpected text at $pos")
            pos += word.length
            return v
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw JsonException("Bad number '$t'")
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated string")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad \\u escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return out
            }
            while (true) {
                out += value()
                skipWs()
                if (atEnd()) throw JsonException("Unterminated array")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw JsonException("Expected , or ] at ${pos - 1}")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return out
            }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw JsonException("Expected a name at $pos")
                val k = str()
                skipWs()
                if (atEnd() || s[pos++] != ':') throw JsonException("Expected : at ${pos - 1}")
                out[k] = value()
                skipWs()
                if (atEnd()) throw JsonException("Unterminated object")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JsonException("Expected , or } at ${pos - 1}")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObj(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Any?.asList(): List<Any?>? = this as? List<Any?>

internal fun Map<String, Any?>.str(k: String): String = (this[k] as? String) ?: ""
internal fun Map<String, Any?>.int(k: String): Int = (this[k] as? Number)?.toInt() ?: 0
internal fun Map<String, Any?>.bool(k: String): Boolean = (this[k] as? Boolean) ?: false
