package brj.reader

import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.source.Source
import java.io.File
import java.net.URI
import java.util.jar.JarFile

private fun fileName(ns: Symbol) = "${ns.baseStr.replace('.', '/')}.brj"

internal class ClasspathLoader(private val truffleEnv: TruffleLanguage.Env,
                               private val sources: Map<Symbol, Source> = emptyMap(),
                               private val forms: Map<Symbol, List<Form>> = emptyMap()) : FormLoader {

    init {
        val cacheDir = File(".brj-stuff/resources")
        System.getProperty("java.class.path").split(File.pathSeparator).reversed().forEach { path ->
            if (path.endsWith(".jar")) {
                JarFile(path).use { jarFile ->
                    jarFile.entries().asSequence().forEach { entry ->
                        if (entry.name.endsWith(".brj")) {
                            jarFile.getInputStream(entry).use { inStream ->
                                File(cacheDir, entry.name).also { it.parentFile.mkdirs() }.outputStream().use { outStream ->
                                    inStream.transferTo(outStream)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val classpathUris = System.getProperty("java.class.path").split(truffleEnv.pathSeparator).mapNotNull {
        val uri = if (it.startsWith("/")) File(it).toURI() else URI(it)
        val truffleFile = truffleEnv.getPublicTruffleFile(uri)

        when {
            !truffleFile.isReadable -> null
            else -> TODO()
        }
    }

    private fun nsSource(ns: Symbol) =
        this::class.java.getResource("/${fileName(ns)}")?.let { Source.newBuilder("brj", it).build() }
            ?: classpathUris.asSequence().mapNotNull { TODO() }.firstOrNull()

    override fun loadForms(ns: Symbol): List<Form> =
        forms[ns] ?: readSourceForms(sources[ns] ?: nsSource(ns) ?: TODO("ns not found: '$ns'"))
}
