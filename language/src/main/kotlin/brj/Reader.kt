package brj

import com.oracle.truffle.api.source.Source
import brj.analyser.Analyser
import brj.reader.NativeLibraryLoader
import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Node
import io.github.treesitter.jtreesitter.Parser
import brj.runtime.Symbol
import brj.runtime.sym
import java.lang.foreign.Arena
import java.math.BigDecimal
import java.math.BigInteger

private val STRING_ESCAPES = mapOf(
    'n' to '\n',
    't' to '\t',
    'r' to '\r',
    '\\' to '\\',
    '"' to '"',
    'b' to '\b',
    'f' to '',
)

private fun unescapeString(raw: String, src: Source, contentStart: Int): String {
    if ('\\' !in raw) return raw

    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c != '\\') {
            sb.append(c)
            i++
            continue
        }

        if (raw.getOrNull(i + 1) == 'u') {
            val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
            if (hex.length != 4 || !hex.all { it.digitToIntOrNull(16) != null })
                throw Analyser.Error(
                    "Malformed \\u escape in string literal, expected four hex digits",
                    src.createSection(contentStart + i, minOf(6, raw.length - i)),
                )
            sb.append(hex.toInt(16).toChar())
            i += 6
        } else {
            val escaped = raw.getOrNull(i + 1)
            val unescaped = escaped?.let { STRING_ESCAPES[it] }
                ?: throw Analyser.Error(
                    "Unrecognised escape sequence '\\${escaped ?: ""}' in string literal",
                    src.createSection(contentStart + i, minOf(2, raw.length - i)),
                )
            sb.append(unescaped)
            i += 2
        }
    }
    return sb.toString()
}

class Reader private constructor(private val src: Source) {
    companion object {
        private val logger = System.getLogger("brj.Reader")

        private val lang: Language =
            try {
                NativeLibraryLoader.loadLibrary("tree-sitter-bridje", Arena.global())
                    .let { Language.load(it, "tree_sitter_bridje") }
            } catch (e: Throwable) {
                logger.log(System.Logger.Level.ERROR, "Failed to load native library", e)
                throw e
            }

        fun Source.readForms(): Sequence<Form> {
            val tree = Parser(lang).parse(characters.toString()).orElseThrow()

            return Reader(this).run {
                tree.rootNode.children.asSequence()
                    .filter { it.type != "comment" }
                    .map { it.readForm() }
            }
        }
    }

    // Tree-sitter reports UTF-8 byte offsets; Truffle Source.createSection expects
    // character offsets. Precompute a byte->char map once per source so non-ASCII
    // input doesn't feed bogus indices into createSection.
    private val byteToChar: IntArray = run {
        val chars = src.characters.toString()
        val charLen = chars.length
        val byteLen = chars.toByteArray(Charsets.UTF_8).size
        val map = IntArray(byteLen + 1)
        var byteIdx = 0
        var charIdx = 0
        while (charIdx < charLen) {
            val cp = chars.codePointAt(charIdx)
            val charCount = Character.charCount(cp)
            val utf8Bytes = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            repeat(utf8Bytes) { map[byteIdx + it] = charIdx }
            byteIdx += utf8Bytes
            charIdx += charCount
        }
        map[byteLen] = charLen
        map
    }

    fun Node.readForm(): Form {
        if (isError) throw RuntimeException("Error reading form: $text")
        val startChar = byteToChar[range.startByte]
        val endChar = byteToChar[range.endByte]
        val loc = src.createSection(startChar, endChar - startChar)

        return when (type) {
            "int" -> IntForm(text!!.toLong(), loc)
            "float" -> DoubleForm(text!!.toDouble(), loc)
            "bigint" -> BigIntForm(BigInteger(text!!.dropLast(1)), loc)
            "bigdec" -> BigDecForm(BigDecimal(text!!.dropLast(1)), loc)
            "string" -> StringForm(unescapeString(text!!.drop(1).dropLast(1), src, startChar + 1), loc)
            "symbol" -> SymbolForm(Symbol.intern(text!!), loc)
            "dot_symbol" -> DotSymbolForm(Symbol.intern(text!!.drop(1)), loc)
            "qualified_dot_symbol" -> {
                // Alias/.member — split on '/.'
                val t = text!!
                val slashDot = t.indexOf("/.")
                QDotSymbolForm(Symbol.intern(t.substring(0, slashDot)), Symbol.intern(t.substring(slashDot + 2)), loc)
            }
            "qualified_symbol" -> {
                val t = text!!
                val slash = t.indexOf('/')
                QSymbolForm(Symbol.intern(t.substring(0, slash)), Symbol.intern(t.substring(slash + 1)), loc)
            }

            "list" -> ListForm(namedChildren.map { it.readForm() }, loc)
            "vector" -> VectorForm(namedChildren.map { it.readForm() }, loc)
            "set" -> SetForm(namedChildren.map { it.readForm() }, loc)
            "record" -> RecordForm(namedChildren.map { it.readForm() }, loc)

            "call" -> {
                val fn = namedChildren[0].readForm()
                val args = namedChildren.drop(1).map { it.readForm() }
                ListForm(listOf(fn) + args, loc)
            }

            "record_sugar" -> {
                val fn = namedChildren[0].readForm()
                val recordFields = namedChildren.drop(1).map { it.readForm() }
                ListForm(listOf(fn, RecordForm(recordFields, loc)), loc)
            }

            "block_call" -> {
                val blockName = namedChildren[0].text!!
                val args = namedChildren.drop(1).flatMap { child ->
                    if (child.type == "block_body") child.namedChildren.map { it.readForm() }
                    else listOf(child.readForm())
                }
                ListForm(listOf(SymbolForm(Symbol.intern(blockName), loc)) + args, loc)
            }

            "quote" -> ListForm(listOf(SymbolForm("quote".sym, loc), namedChildren[0].readForm()), loc)
            "syntax_quote" -> ListForm(listOf(SymbolForm("squote".sym, loc), namedChildren[0].readForm()), loc)
            "unquote" -> ListForm(listOf(SymbolForm("unquote".sym, loc), namedChildren[0].readForm()), loc)
            "unquote_splice" -> ListForm(listOf(SymbolForm("unquoteSplicing".sym, loc), namedChildren[0].readForm()), loc)

            "metadata" -> {
                val metaValue = namedChildren[0].readForm()
                val innerForm = namedChildren[1].readForm()
                when (metaValue) {
                    is DotSymbolForm -> innerForm.withStaticMeta(metaValue)
                    is QDotSymbolForm -> innerForm.withStaticMeta(metaValue)
                    is RecordForm -> innerForm.withStaticMeta(metaValue)
                    else -> error("metadata must be a member or a record")
                }
            }

            else -> error("Unknown form type: $type")
        }
    }
}
