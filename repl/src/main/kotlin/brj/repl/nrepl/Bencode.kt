package brj.repl.nrepl

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

object Bencode {

    fun encode(value: Any?): ByteArray =
        ByteArrayOutputStream().also { encodeValue(value, it) }.toByteArray()

    private fun encodeValue(value: Any?, out: ByteArrayOutputStream) {
        when (value) {
            null -> encodeString("", out)
            is String -> encodeString(value, out)
            is Int -> encodeInt(value.toLong(), out)
            is Long -> encodeInt(value, out)
            is Map<*, *> -> encodeMap(value, out)
            is List<*> -> encodeList(value, out)
            is Set<*> -> encodeList(value.toList(), out)
            else -> encodeString(value.toString(), out)
        }
    }

    private fun encodeString(s: String, out: ByteArrayOutputStream) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.write("${bytes.size}:".toByteArray(Charsets.UTF_8))
        out.write(bytes)
    }

    private fun encodeInt(n: Long, out: ByteArrayOutputStream) {
        out.write("i${n}e".toByteArray(Charsets.UTF_8))
    }

    private fun encodeList(list: List<*>, out: ByteArrayOutputStream) {
        out.write('l'.code)
        list.forEach { encodeValue(it, out) }
        out.write('e'.code)
    }

    private fun encodeMap(map: Map<*, *>, out: ByteArrayOutputStream) {
        out.write('d'.code)
        map.entries.sortedBy { it.key.toString() }.forEach { (key, value) ->
            encodeString(key.toString(), out)
            encodeValue(value, out)
        }
        out.write('e'.code)
    }

    fun decode(input: InputStream): Any? {
        val pushback = input as? PushbackInputStream ?: PushbackInputStream(input)
        return decodeValue(pushback)
    }

    private fun decodeValue(input: PushbackInputStream): Any? {
        val ch = input.read()
        if (ch == -1) return null

        return when (ch.toChar()) {
            'i' -> decodeInt(input)
            'l' -> decodeList(input)
            'd' -> decodeDict(input)
            in '0'..'9' -> decodeString(input, ch.toChar())
            else -> throw IllegalArgumentException("Invalid bencode: unexpected '${ch.toChar()}'")
        }
    }

    private fun decodeInt(input: PushbackInputStream): Long {
        val sb = StringBuilder()
        while (true) {
            val ch = input.read()
            if (ch == -1) throw IllegalArgumentException("Unexpected EOF in integer")
            if (ch.toChar() == 'e') break
            sb.append(ch.toChar())
        }
        return sb.toString().toLong()
    }

    private fun decodeList(input: PushbackInputStream): List<Any?> {
        val list = mutableListOf<Any?>()
        while (true) {
            val ch = input.read()
            if (ch == -1) throw IllegalArgumentException("Unexpected EOF in list")
            if (ch.toChar() == 'e') break
            input.unread(ch)
            list.add(decodeValue(input) ?: throw IllegalArgumentException("Malformed bencode in list"))
        }
        return list
    }

    private fun decodeDict(input: PushbackInputStream): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        while (true) {
            val ch = input.read()
            if (ch == -1) throw IllegalArgumentException("Unexpected EOF in dictionary")
            if (ch.toChar() == 'e') break
            val key = if (ch.toChar() in '0'..'9') decodeString(input, ch.toChar())
                      else throw IllegalArgumentException("Dictionary key must be a string")
            map[key] = decodeValue(input)
        }
        return map
    }

    private fun decodeString(input: PushbackInputStream, firstChar: Char): String {
        val lengthStr = StringBuilder().apply { append(firstChar) }
        while (true) {
            val ch = input.read()
            if (ch == -1) throw IllegalArgumentException("Unexpected EOF in string length")
            if (ch.toChar() == ':') break
            lengthStr.append(ch.toChar())
        }
        val length = lengthStr.toString().toInt()
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n == -1) throw IllegalArgumentException("Unexpected EOF in string content")
            read += n
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun write(output: OutputStream, value: Any?) {
        output.write(encode(value))
        output.flush()
    }
}
