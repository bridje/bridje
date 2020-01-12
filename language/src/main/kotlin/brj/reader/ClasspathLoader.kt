package brj.reader

import brj.reader.ClasspathLoader.SourceRoot.FileSourceRoot
import brj.reader.ClasspathLoader.SourceRoot.JarSourceRoot
import brj.reader.FormReader.SourceFormReader.readSourceForms
import brj.runtime.SymKind
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.io.ByteSequence
import java.io.File
import java.lang.ref.SoftReference
import java.net.URI
import java.util.jar.JarInputStream

private fun fileName(ns: Symbol) = "${ns.baseStr.replace('.', '/')}.brj"

internal class ClasspathLoader(private val truffleEnv: TruffleLanguage.Env,
                               private val sources: Map<Symbol, Source> = emptyMap(),
                               private val forms: Map<Symbol, List<Form>> = emptyMap()) : FormLoader {

    private sealed class SourceRoot {
        abstract fun source(ns: Symbol): Source?

        data class JarSourceRoot(val file: TruffleFile) : SourceRoot() {
            private var cache: MutableMap<Symbol, SoftReference<ByteArray>>

            private fun foreachEntry(f: (Symbol, ByteArray) -> Unit) {
                JarInputStream(file.newInputStream()).use {
                    it.run {
                        var entry = nextJarEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".brj"))
                                f(Symbol(SymKind.ID, entry.name), readAllBytes())
                            entry = nextJarEntry
                        }
                    }
                }
            }

            init {
                val cache: MutableMap<Symbol, SoftReference<ByteArray>> = mutableMapOf()
                foreachEntry { symbol, bytes -> cache[symbol] = SoftReference(bytes) }
                this.cache = cache
            }

            override fun source(ns: Symbol): Source? {
                val ref = cache[ns] ?: return null
                var byteArray = ref.get()

                if (byteArray == null) {
                    foreachEntry { symbol, bytes -> cache[symbol] = SoftReference(bytes) }
                    byteArray = cache[ns]!!.get()
                }

                return Source.newBuilder("brj", ByteSequence.create(byteArray), ns.toString()).build()
            }
        }

        data class FileSourceRoot(val file: TruffleFile) : SourceRoot() {
            override fun source(ns: Symbol): Source? =
                file.resolve(fileName(ns))
                    .takeIf { it.isReadable }
                    ?.let { Source.newBuilder("brj", it).build() }
        }
    }

    private val classpathUris = System.getProperty("java.class.path").split(truffleEnv.pathSeparator).mapNotNull {
        val uri = if (it.startsWith("/")) File(it).toURI() else URI(it)
        val truffleFile = truffleEnv.getPublicTruffleFile(uri)

        when {
            !truffleFile.isReadable -> null
            it.endsWith(".jar") -> JarSourceRoot(truffleFile)
            else -> FileSourceRoot(truffleFile)
        }
    }

    private fun nsSource(ns: Symbol) =
        this::class.java.getResource("/${fileName(ns)}")?.let { Source.newBuilder("brj", it).build() }
            ?: classpathUris.asSequence().mapNotNull { it.source(ns) }.firstOrNull()

    override fun loadForms(ns: Symbol): List<Form> =
        forms[ns] ?: readSourceForms(sources[ns] ?: nsSource(ns) ?: TODO("ns not found: '$ns'"))
}
