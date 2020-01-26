package brj.reader

import brj.BridjeContext
import brj.reader.ClasspathLoader.Companion.ClasspathEntry.FileClasspathEntry
import brj.reader.ClasspathLoader.Companion.ClasspathEntry.JarClasspathEntry
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.source.Source
import java.io.File
import java.util.*
import java.util.jar.JarFile

internal class ClasspathLoader(private val ctx: BridjeContext) : FormLoader {

    companion object {
        private sealed class ClasspathEntry {
            class FileClasspathEntry(val path: String) : ClasspathEntry() {
                fun nsFile(fileName: String, ctx: BridjeContext) =
                    ctx.truffleEnv.getPublicTruffleFile(path).resolve(fileName).takeIf { it.isReadable }
            }

            class JarClasspathEntry(val path: String) : ClasspathEntry() {
                private val entries by lazy { JarFile(path).entries().asSequence().map { it.name }.toSet() }
                private val cacheFileName by lazy {
                    val file = File(path)
                    val hash = String.format("%02x", Arrays.hashCode(file.inputStream().readAllBytes()))
                    "${file.name.removeSuffix(".jar")}-$hash"
                }

                fun nsFile(fileName: String, cacheDir: TruffleFile) =
                    if (entries.contains(fileName)) {
                        val cacheFile = cacheDir.resolve(cacheFileName).resolve(fileName)
                        if (!cacheFile.isReadable) {
                            JarFile(path).use { jarFile ->
                                jarFile.getInputStream(jarFile.getJarEntry(fileName)).use { iStream ->
                                    cacheFile.also { it.parent.createDirectories() }.newOutputStream().use { oStream ->
                                        iStream.copyTo(oStream)
                                    }
                                }
                            }
                        }

                        cacheFile.takeIf { it.isReadable }
                    } else null
            }
        }

        private val classpathEntries by lazy {
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { path -> if (path.endsWith(".jar")) JarClasspathEntry(path) else FileClasspathEntry(path) }
        }
    }

    private val brjHome: String? = System.getProperty("brj.home")
    private val stdlibDir = ctx.truffleEnv.getInternalTruffleFile(if (brjHome != null) "$brjHome/stdlib" else "src/main/brj")
        .also { assert(it.isReadable) }

    val brjStuffPath = ctx.truffleEnv.config["brj.brj-stuff-path"] as? String ?: ".brj-stuff"
    private val cacheDir: TruffleFile = ctx.truffleEnv.getPublicTruffleFile(brjStuffPath)
        .resolve("resources")
        .also { it.createDirectories() }

    private fun nsSource(ns: Symbol): Source? {
        val fileName = "${ns.baseStr.replace('.', '/')}.brj"

        return stdlibDir.resolve(fileName)?.let { Source.newBuilder("brj", it).build() }
            ?: classpathEntries.asSequence()
                .mapNotNull { entry ->
                    when (entry) {
                        is FileClasspathEntry -> entry.nsFile(fileName, ctx)
                        is JarClasspathEntry -> entry.nsFile(fileName, cacheDir)
                    }
                }
                .firstOrNull()
                ?.let { file -> Source.newBuilder("brj", file).build() }
    }

    override fun loadForms(ns: Symbol): List<Form> = readSourceForms(nsSource(ns) ?: TODO("ns not found: '$ns'"))
}
