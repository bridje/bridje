package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FsTest {

    private fun Path.brjLit(): String = toString().replace("\\", "\\\\")

    @Test
    fun `file creates a File tag`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("a.txt")
        Files.writeString(target, "hi")

        val ns = ctx.evalBridje("""
            ns: test.fs.file
              require:
                brj:
                  fs
            def: result fs/file("${target.brjLit()}")
        """.trimIndent())

        val result = ns.getMember("result")
        assertEquals("File", result.metaObject.metaSimpleName)
    }

    @Test
    fun `exists? reflects filesystem state`(@TempDir dir: Path) = withContext { ctx ->
        val present = dir.resolve("present.txt").also { Files.writeString(it, "") }
        val absent = dir.resolve("absent.txt")

        val yes = ctx.evalBridje("""
            ns: test.fs.exists1
              require:
                brj:
                  fs
            def: result fs/exists?(fs/file("${present.brjLit()}"))
        """.trimIndent()).getMember("result")
        assertTrue(yes.asBoolean())

        val no = ctx.evalBridje("""
            ns: test.fs.exists2
              require:
                brj:
                  fs
            def: result fs/exists?(fs/file("${absent.brjLit()}"))
        """.trimIndent()).getMember("result")
        assertFalse(no.asBoolean())
    }

    @Test
    fun `readString returns file contents`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("hello.txt")
        Files.writeString(target, "hello, world")

        val result = ctx.evalBridje("""
            ns: test.fs.read
              require:
                brj:
                  fs
            def: result fs/readString(fs/file("${target.brjLit()}"))
        """.trimIndent()).getMember("result")

        assertEquals("hello, world", result.asString())
    }

    @Test
    fun `isFile and isDir classify entries`(@TempDir dir: Path) = withContext { ctx ->
        val file = dir.resolve("a.txt").also { Files.writeString(it, "x") }

        val isFile = ctx.evalBridje("""
            ns: test.fs.isfile
              require:
                brj:
                  fs
            def: result fs/isFile?(fs/file("${file.brjLit()}"))
        """.trimIndent()).getMember("result")
        assertTrue(isFile.asBoolean())

        val isDir = ctx.evalBridje("""
            ns: test.fs.isdir
              require:
                brj:
                  fs
            def: result fs/isDir?(fs/file("${dir.brjLit()}"))
        """.trimIndent()).getMember("result")
        assertTrue(isDir.asBoolean())
    }

    @Test
    fun `list enumerates directory entries`(@TempDir dir: Path) = withContext { ctx ->
        Files.writeString(dir.resolve("a.txt"), "")
        Files.writeString(dir.resolve("b.txt"), "")

        val result = ctx.evalBridje("""
            ns: test.fs.list
              require:
                brj:
                  fs
            def: result fs/list(fs/file("${dir.brjLit()}"))
        """.trimIndent()).getMember("result")

        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)
    }

    @Test
    fun `resolve joins paths`(@TempDir dir: Path) = withContext { ctx ->
        Files.writeString(dir.resolve("a.txt"), "content")

        val result = ctx.evalBridje("""
            ns: test.fs.resolve
              require:
                brj:
                  fs
            def: result fs/readString(fs/resolve(fs/file("${dir.brjLit()}"), "a.txt"))
        """.trimIndent()).getMember("result")

        assertEquals("content", result.asString())
    }

    @Test
    fun `name returns the final path segment`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("thing.txt").also { Files.writeString(it, "") }

        val result = ctx.evalBridje("""
            ns: test.fs.name
              require:
                brj:
                  fs
            def: result fs/name(fs/file("${target.brjLit()}"))
        """.trimIndent()).getMember("result")

        assertEquals("thing.txt", result.asString())
    }
}
