package brj.reader

import brj.BridjeContext
import brj.BridjeLanguage
import brj.reader.ClasspathLoader.PathEntry.DirPathEntry
import brj.reader.ClasspathLoader.PathEntry.JarPathEntry
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.source.Source
import java.util.jar.JarFile

internal class ClasspathLoader(private val ctx: BridjeContext) : FormLoader {
    private sealed class PathEntry {
        protected abstract fun nsFile(fileName: String): TruffleFile?

        fun nsFile(ns: Symbol): TruffleFile? = nsFile("${ns.baseStr.replace('.', '/')}.brj")

        class JarPathEntry(val jarFile: TruffleFile, val cacheDir: TruffleFile) : PathEntry() {
            private val entries by lazy { JarFile(jarFile.path).entries().asSequence()
                .filter { it.name.endsWith(".brj") }
                .associateBy { it.name } }

            override fun nsFile(fileName: String) =
                entries[fileName]?.let { jarEntry ->

                    val hash = String.format("%02x", jarEntry.crc)
                    val cacheFile = cacheDir
                        .resolve(jarFile.name.removeSuffix(".jar"))
                        .resolve(jarEntry.name)
                        .resolveSibling("${jarEntry.name.removeSuffix(".brj")}-$hash.brj")

                    if (!cacheFile.isReadable) {
                        JarFile(jarFile.path).use { jarFile ->
                            cacheFile.parent.createDirectories()

                            jarFile.getInputStream(jarEntry).use { inStream ->
                                cacheFile.newOutputStream().use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                        }
                    }

                    cacheFile
                }
        }

        class DirPathEntry(val dir: TruffleFile) : PathEntry() {
            override fun nsFile(fileName: String) =
                dir.resolve(fileName).takeIf { it.isReadable }
        }
    }

    private val pathEntries: List<PathEntry>

    init {
        val stdlib = ctx.brjHome?.let { brjHome ->
            DirPathEntry(
                ctx.truffleEnv.getInternalTruffleFile("${brjHome}/stdlib")
                    .also { assert(it.isReadable) })
        }

        val stuffDir = ctx.truffleEnv.getPublicTruffleFile(ctx.truffleEnv.options[BridjeLanguage.STUFF_DIR])

        val cacheDir = stuffDir.resolve("jar-cache")

        pathEntries = listOfNotNull(stdlib) +
            ctx.truffleEnv.options[BridjeLanguage.PATH]
                .split(ctx.truffleEnv.pathSeparator)
                .map { ctx.truffleEnv.getPublicTruffleFile(it) }
                .filter { it.isReadable }
                .map { if (it.isRegularFile()) JarPathEntry(it, cacheDir) else DirPathEntry(it) }
    }

    private fun nsSource(ns: Symbol): Source? =
        pathEntries.asSequence().mapNotNull { it.nsFile(ns) }.firstOrNull()?.let { Source.newBuilder("brj", it).build() }

    override fun loadForms(ns: Symbol): List<Form> = readSourceForms(nsSource(ns) ?: TODO("ns not found: '$ns'"))

}
