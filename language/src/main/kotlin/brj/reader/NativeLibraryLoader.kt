package brj.reader

import io.github.treesitter.jtreesitter.NativeLibraryLookup
import java.io.File.createTempFile
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup

object NativeLibraryLoader {
    private val logger = System.getLogger("brj.NativeLibraryLoader")

    fun loadLibrary(name: String, arena: Arena): SymbolLookup {
        val path = extractLibrary(name)
        return SymbolLookup.libraryLookup(path, arena)
    }

    private fun extractLibrary(name: String): String {
        val osName = System.getProperty("os.name")!!.lowercase()
        val archName = System.getProperty("os.arch")!!.lowercase()

        val (os, ext, prefix) = when {
            "windows" in osName -> Triple("windows", "dll", "")
            "linux" in osName -> Triple("linux", "so", "lib")
            "mac" in osName -> Triple("macos", "dylib", "lib")
            else -> throw UnsupportedOperationException("Unsupported operating system: $osName")
        }

        val arch = when {
            "amd64" in archName || "x86_64" in archName -> "x64"
            "aarch64" in archName || "arm64" in archName -> "aarch64"
            else -> throw UnsupportedOperationException("Unsupported architecture: $archName")
        }

        val libPath = "/native/$os/$arch/$prefix$name.$ext"
        val libUrl = NativeLibraryLoader::class.java.getResource(libPath)
            ?: throw IllegalStateException("Native library not found: $libPath")

        return createTempFile(prefix + name, ".$ext").apply {
            writeBytes(libUrl.openStream().use { it.readAllBytes() })
            deleteOnExit()
        }.path
    }

    class TreeSitterLookup : NativeLibraryLookup {
        override fun get(arena: Arena): SymbolLookup {
            return loadLibrary("tree-sitter", arena)
        }
    }
}
