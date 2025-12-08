package brj

import com.oracle.truffle.api.source.Source
import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Node
import io.github.treesitter.jtreesitter.Parser
import java.io.File.createTempFile
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup

class Reader private constructor(private val src: Source) {
    companion object {
        private const val LIB_NAME = "tree-sitter-bridje"

        private fun libPath(): String? {
            val osName = System.getProperty("os.name")!!.lowercase()
            val archName = System.getProperty("os.arch")!!.lowercase()
            val ext: String
            val os: String
            val prefix: String
            when {
                "windows" in osName -> {
                    ext = "dll"
                    os = "windows"
                    prefix = ""
                }

                "linux" in osName -> {
                    ext = "so"
                    os = "linux"
                    prefix = "lib"
                }

                "mac" in osName -> {
                    ext = "dylib"
                    os = "macos"
                    prefix = "lib"
                }

                else -> {
                    throw UnsupportedOperationException("Unsupported operating system: $osName")
                }
            }
            val arch = when {
                "amd64" in archName || "x86_64" in archName -> "x64"
                "aarch64" in archName || "arm64" in archName -> "aarch64"
                else -> throw UnsupportedOperationException("Unsupported architecture: $archName")
            }
            val libPath = "/native/$os/$arch/$prefix$LIB_NAME.$ext"
            val libUrl = javaClass.getResource(libPath) ?: return null
            return createTempFile(prefix + LIB_NAME, ".$ext").apply {
                writeBytes(libUrl.openStream().use { it.readAllBytes() })
                deleteOnExit()
            }.path
        }

        private val lang: Language =
            SymbolLookup.libraryLookup(libPath(), Arena.global())
                .let { Language.load(it, "tree_sitter_bridje") }

        fun Source.readForms(): Sequence<Form> {
            val tree = Parser(lang).parse(characters.toString()).orElseThrow()

            return Reader(this).run {
                tree.rootNode.children.asSequence().map { it.readForm() }
            }
        }
    }


    fun Node.readForm(): Form {
        if (isError) throw RuntimeException("Error reading form: $text")
        val loc = src.createSection(range.startByte, range.endByte - range.startByte)

        return when (type) {
            "int" -> IntForm(text!!.toLong(), loc)
            "float" -> DoubleForm(text!!.toDouble(), loc)
            "string" -> StringForm(text!!.drop(1).dropLast(1), loc)
            "symbol" -> SymbolForm(text!!, loc)

            "list" -> ListForm(namedChildren.map { it.readForm() }, loc)
            "vector" -> VectorForm(namedChildren.map { it.readForm() }, loc)
            "set" -> SetForm(namedChildren.map { it.readForm() }, loc)
            "map" -> MapForm(namedChildren.map { it.readForm() }, loc)

            "call" -> {
                val fnName = namedChildren[0].text!!.dropLast(1)
                val args = namedChildren.drop(1).map { it.readForm() }
                ListForm(listOf(SymbolForm(fnName, loc)) + args, loc)
            }

            "method_call" -> {
                val receiver = namedChildren[0].readForm()
                val methodName = namedChildren[1].text!!.drop(1).dropLast(1)
                val args = namedChildren.drop(2).map { it.readForm() }
                ListForm(listOf(SymbolForm(methodName, loc), receiver) + args, loc)
            }

            "field_access" -> {
                val receiver = namedChildren[0].readForm()
                val fieldName = namedChildren[1].text!!.drop(1)
                ListForm(listOf(KeywordForm(fieldName, loc), receiver), loc)
            }

            "anon_fn" -> {
                val body = namedChildren[0].readForm()
                ListForm(listOf(SymbolForm("fn"), VectorForm(listOf(SymbolForm("it"))), body), loc)
            }

            "block_call" -> {
                val blockName = namedChildren[0].text!!.dropLast(1) // remove trailing ':'
                val args = namedChildren.drop(1).flatMap { child ->
                    if (child.type == "block_body") child.namedChildren.map { it.readForm() }
                    else listOf(child.readForm())
                }
                ListForm(listOf(SymbolForm(blockName, loc)) + args, loc)
            }

            else -> error("Unknown form type: $type")
        }
    }
}