package brj

import brj.runtime.Symbol
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

class FormReader internal constructor(private val source: Source) : AutoCloseable {
    companion object {
        private const val identifierChars = "+-*%$£!<>?."
        private const val breakChars = "([{}])#@^%"
    }

    private fun Char.isBreakChar() = this.isWhitespace() || this == ',' || breakChars.contains(this)
    private fun Char.isBridjeIdentifierStart() = isJavaIdentifierStart() || identifierChars.contains(this)
    private fun Char.isBridjeIdentifierPart() = isJavaIdentifierPart() || identifierChars.contains(this)

    private val rdr = source.reader
    private var charIndex = 0
    private var unreadBuffer: Char? = null

    private fun unreadChar(c: Char) {
        charIndex--
        unreadBuffer = c
    }

    private fun readChar(): Char? {
        return unreadBuffer?.let { unreadBuffer = null; charIndex++; it }
            ?: rdr.read().let {
                if (it == -1) null else {
                    charIndex++; it.toChar()
                }
            }
    }

    private fun readNonBreakChar(): Char? {
        val c = readChar() ?: return null

        return if (c.isBreakChar()) {
            unreadChar(c); null
        } else c
    }

    override fun close() {
        rdr.close()
    }

    private fun readNumber(): Form {
        val sb = StringBuilder()
        val startIndex = charIndex

        while (true) {
            val c = readNonBreakChar() ?: break
            if (!c.isDigit()) TODO()
            sb.append(c)
        }

        return IntForm(sb.toString().toInt(), source.createSection(startIndex, sb.length))
    }

    private fun readColl(endChar: Char, startIndex: Int = charIndex, f: (List<Form>, SourceSection) -> Form): Form {
        readChar()
        val forms = readForms(endChar).toList()
        return f(forms, source.createSection(startIndex, charIndex - startIndex))
    }

    private fun readList() = readColl(')', f = ::ListForm)
    private fun readVector() = readColl(']', f = ::VectorForm)
    private fun readRecord() = readColl('}', f = ::RecordForm)
    private fun readSet(startIndex: Int) = readColl('}', startIndex, f = ::SetForm)

    private fun readHash(): Form {
        val startIndex = charIndex
        readChar()
        return when ((readChar() ?: TODO()).also { unreadChar(it) }) {
            '{' -> readSet(startIndex)
            else -> TODO()
        }
    }

    private fun readString(): Form {
        val sb = StringBuilder()
        val startIndex = charIndex
        readChar()

        while (true) {
            when (val c = readChar() ?: TODO()) {
                '"' -> break
                '\\' -> {
                    sb.append(
                        when (readChar() ?: TODO()) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> TODO()
                        }
                    )
                }
                else -> sb.append(c)
            }
        }

        return StringForm(sb.toString(), source.createSection(startIndex, charIndex - startIndex))
    }

    private fun readIdent(f: (Symbol?, String, SourceSection) -> Form): Form {
        val beforeSlash = StringBuilder()
        val afterSlash = StringBuilder()

        val startIndex = charIndex

        var seenSlash = false

        while (true) {
            val c = readNonBreakChar()

            when {
                c == null -> break
                c == '/' -> if (seenSlash) TODO() else seenSlash = true
                !c.isBridjeIdentifierPart() -> TODO("invalid identifier part: '$c'")
                else -> if (seenSlash) afterSlash.append(c) else beforeSlash.append(c)
            }
        }

        val ns = if (seenSlash)
            symbol(beforeSlash.toString()
                .also { if (it.startsWith('.')) TODO() })
        else null

        val local = if (seenSlash) afterSlash.toString() else beforeSlash.toString()
        val loc = source.createSection(startIndex, charIndex - startIndex)

        return f(ns, local, loc)
    }

    private fun readSymbol() = readIdent { ns, local, loc ->
        if (ns == null) {
            when {
                local == "true" -> BoolForm(true, loc)
                local == "false" -> BoolForm(false, loc)
                local == "nil" -> NilForm(loc)
                local.startsWith('.') -> DotSymbolForm(symbol(local.substring(1)), loc)
                else -> SymbolForm(symbol(local), loc)
            }
        } else SymbolForm(symbol(ns, local), loc)
    }

    private fun readKeyword(): Form {
        readChar()

        return readIdent { ns, local, loc ->
            if (ns != null) TODO()
            if (local.startsWith('.')) TODO()
            KeywordForm(symbol(ns, local), loc)
        }
    }

    private fun readForm(): Form {
        val c = (readChar() ?: TODO()).also { unreadChar(it) }
        return when {
            c.isDigit() -> readNumber()
            c == '(' -> readList()
            c == '[' -> readVector()
            c == '{' -> readRecord()
            c == '#' -> readHash()
            c == '"' -> readString()
            c == ':' -> readKeyword()
            c.isBridjeIdentifierStart() -> readSymbol()
            else -> TODO()
        }
    }

    private fun readForms(endChar: Char? = null) = sequence {
        while (true) {
            val c = readChar() ?: if (endChar != null) TODO() else break
            when {
                c.isWhitespace() || c == ',' -> continue

                c == endChar -> break
                c == ')' || c == ']' || c == '}' -> TODO()
                c == ';' -> while (true) if (readChar() == '\n') break

                else -> {
                    unreadChar(c);
                    yield(readForm())
                }
            }
        }
    }

    fun readForms() = readForms(endChar = null)
}
