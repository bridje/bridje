package brj

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

class Reader private constructor(private val src: Source) {
    private val chars = src.characters
    private val eof = chars.length

    private var pos = 0

    private fun loc(startPos: Int) = src.createSection(startPos, pos - startPos)

    private fun skipComment() {
        while (pos < eof) {
            when (chars[pos++]) {
                '\r' -> {
                    if (chars[pos] == '\n') pos++
                    break
                }

                '\n' -> break
            }
        }
    }

    private fun readNumberForm(): Form {
        val startPos = pos
        var seenDot = false

        val v = buildString {
            while (pos < eof) {
                val char = chars[pos]
                when (char) {
                    in '0'..'9' -> append(char)
                    '.' -> {
                        append(char)
                        if (seenDot) break
                        seenDot = true
                    }

                    else -> break
                }
                pos++
            }
        }

        return if (seenDot) DoubleForm(v.toDouble(), loc(startPos)) else IntForm(v.toLong(), loc(startPos))
    }

    private fun readStringForm(): Form {
        val startPos = pos++

        val s = buildString {
            while (true) {
                if (pos == eof) error("eof in string")
                when (val char = chars[pos++]) {
                    '"' -> break
                    '\\' -> {
                        if (pos == eof) break
                        when (val escapeChar = chars[pos++]) {
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            else -> error("unknown escape sequence: \\$escapeChar")
                        }
                    }

                    else -> append(char)
                }
            }
        }

        return StringForm(s, loc(startPos))
    }

    private fun readCollForm(startPos: Int, endChar: Char, f: (List<Form>, SourceSection) -> Form): Form =
        f(readForms(endChar).toList(), loc(startPos))

    private fun readForms(until: Char? = null) = sequence<Form> {
        while (pos < eof) {
            when (val ch = chars[pos]) {
                ' ', ',', '\n', '\r', '\t' -> pos++

                ';' -> skipComment()

                in '1'..'9' -> yield(readNumberForm())
                '"' -> yield(readStringForm())

                until -> {
                    pos++; return@sequence
                }

                '(' -> yield(readCollForm(pos++, ')', ::ListForm))
                '[' -> yield(readCollForm(pos++, ']', ::VectorForm))
                '{' -> yield(readCollForm(pos++, '}', ::MapForm))

                '#' -> {
                    val startPos = pos++
                    when (val ch2 = chars[pos++]) {
                        '{' -> yield(readCollForm(startPos, '}', ::SetForm))
                        else -> error("unexpected character after #: $ch2")
                    }
                }

                ')' -> error("unexpected )")
                '}' -> error("unexpected }")
                ']' -> error("unexpected ]")

                else -> error("unexpected character: $ch")
            }
        }

        if (until != null) error("unexpected EOF, expected $until")
    }

    companion object {
        fun Source.readForms() = Reader(this).readForms()
    }
}