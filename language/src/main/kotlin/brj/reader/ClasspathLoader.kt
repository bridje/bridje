package brj.reader

import brj.BridjeContext
import brj.reader.ClasspathLoader.Companion.ClasspathEntry.FileClasspathEntry
import brj.reader.ClasspathLoader.Companion.ClasspathEntry.JarClasspathEntry
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.Symbol
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.source.Source
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

internal class ClasspathLoader(private val ctx: BridjeContext) : FormLoader {

    companion object {
        private sealed class ClasspathEntry {
            data class FileClasspathEntry(val path: String) : ClasspathEntry()
            data class JarClasspathEntry(val path: String, val cacheFileName: String) : ClasspathEntry()
        }

        private val digest = MessageDigest.getInstance("SHA-256")

        private val classpathEntries: List<ClasspathEntry> =
            System.getProperty("java.class.path").split(File.pathSeparator).map { path ->
                val match = Regex("/.+/(.+).jar").matchEntire(path)

                if (match != null) {
                    digest.reset()
                    digest.update(File(path).inputStream().readAllBytes())
                    val hash = digest.digest().take(4).joinToString("") { byte -> String.format("%02x", byte) }
                    JarClasspathEntry(path, "${match.groups[1]!!.value}-$hash")
                } else FileClasspathEntry(path)
            }
    }

    private val cacheDir: TruffleFile = ctx.truffleEnv.getPublicTruffleFile(ctx.truffleEnv.config["brj.brj-stuff-path"] as? String
        ?: ".brj-stuff")
        .resolve("resources")
        .also { it.createDirectories() }

    private fun copyJar(jarFilePath: String) {
        JarFile(jarFilePath).use { jarFile ->
            jarFile.entries().asSequence().forEach { jarEntry ->
                if (jarEntry.name.endsWith(".brj")) {
                    jarFile.getInputStream(jarEntry).use { inStream ->
                        cacheDir.resolve(jarEntry.name)
                            .also { it.parent.createDirectories() }
                            .newOutputStream().use { outStream -> inStream.transferTo(outStream) }
                    }
                }
            }
        }
    }

    private fun entryRootDir(entry: ClasspathEntry) =
        when (entry) {
            is FileClasspathEntry -> ctx.truffleEnv.getPublicTruffleFile(entry.path)
            is JarClasspathEntry -> cacheDir.resolve(entry.cacheFileName).also { jarCacheDir ->
                if (!jarCacheDir.exists()) jarCacheDir.createDirectories(); copyJar(entry.path)
            }
        }

    private fun nsSource(ns: Symbol): Source? {
        val fileName = "${ns.baseStr.replace('.', '/')}.brj"

        return this::class.java.getResource("/$fileName")?.let { Source.newBuilder("brj", it).build() }
            ?: classpathEntries.asSequence()
                .map(::entryRootDir)
                .mapNotNull { dir -> dir.resolve(fileName).takeIf(TruffleFile::isReadable) }
                .firstOrNull()
                ?.let { file -> Source.newBuilder("brj", file).build() }
    }

    override fun loadForms(ns: Symbol): List<Form> = readSourceForms(nsSource(ns) ?: TODO("ns not found: '$ns'"))
}
