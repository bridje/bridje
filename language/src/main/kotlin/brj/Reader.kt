package brj

import com.oracle.truffle.api.source.Source
import brj.reader.NativeLibraryLoader
import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Node
import io.github.treesitter.jtreesitter.Parser
import brj.runtime.Symbol
import brj.runtime.sym
import java.lang.foreign.Arena
import java.math.BigDecimal
import java.math.BigInteger

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
            "string" -> StringForm(text!!.drop(1).dropLast(1), loc)
            "symbol" -> SymbolForm(Symbol.intern(text!!), loc)
            "keyword" -> KeywordForm(Symbol.intern(text!!.drop(1)), loc)
            "qualified_keyword" -> {
                // :ns/member — drop leading ':', split on '/'
                val t = text!!.drop(1)
                val slash = t.indexOf('/')
                QKeywordForm(Symbol.intern(t.substring(0, slash)), Symbol.intern(t.substring(slash + 1)), loc)
            }
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
                if (slash >= 0) {
                    QSymbolForm(Symbol.intern(t.substring(0, slash)), Symbol.intern(t.substring(slash + 1)), loc)
                } else {
                    // Dotted namespace name: split on last dot
                    val lastDot = t.lastIndexOf('.')
                    QSymbolForm(Symbol.intern(t.substring(0, lastDot)), Symbol.intern(t.substring(lastDot + 1)), loc)
                }
            }

            "list" -> ListForm(namedChildren.map { it.readForm() }, loc)
            "vector" -> VectorForm(namedChildren.map { it.readForm() }, loc)
            "set" -> SetForm(namedChildren.map { it.readForm() }, loc)
            "map" -> RecordForm(namedChildren.map { it.readForm() }, loc)

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
            "syntax_quote" -> {
                val inner = namedChildren[0].readForm()
                if (inner !is SymbolForm && inner !is QSymbolForm)
                    error("syntax quote must contain a symbol: $text")
                SyntaxQuoteForm(inner, loc)
            }
            "unquote" -> UnquoteForm(namedChildren[0].readForm(), loc)
            "unquote_splice" -> UnquoteSpliceForm(namedChildren[0].readForm(), loc)

            "metadata" -> {
                val metaValue = namedChildren[0].readForm()
                val innerForm = namedChildren[1].readForm()
                when (metaValue) {
                    is KeywordForm -> innerForm.withMeta(metaValue)
                    is QKeywordForm -> innerForm.withMeta(metaValue)
                    is RecordForm -> innerForm.withMeta(metaValue)
                    else -> error("metadata must be keyword or map")
                }
            }

            else -> error("Unknown form type: $type")
        }
    }
}
