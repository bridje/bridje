package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocTest {

    private fun Path.brjLit(): String = toString().replace("\\", "\\\\")

    @Test
    fun `Loc exposes source name`() = withContext { ctx ->
        val loc = ctx.evalBridjeForms(":rdr/loc(meta('foo))")

        assertEquals("Loc", loc.metaObject.metaSimpleName)
        assertFalse(loc.getMember("source").isNull)
    }

    @Test
    fun `Loc path is null for string-based source`() = withContext { ctx ->
        val loc = ctx.evalBridjeForms(":rdr/loc(meta('foo))")

        assertTrue(loc.getMember("path").isNull, "string-based source must not have a path")
    }

    @Test
    fun `Loc path is set for file-based source`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("src.brj")
        Files.writeString(target, "foo\n")

        val loc = ctx.evalBridje(
            """
            ns: test.loc.file
              require:
                brj:
                  rdr
                  fs

            def: result :rdr/loc(meta(first(rdr/<-file(fs/file("${target.brjLit()}")))))
            """.trimIndent()
        ).getMember("result")

        assertEquals("Loc", loc.metaObject.metaSimpleName)
        assertEquals(target.toAbsolutePath().toString(), loc.getMember("path").asString())
        assertTrue(loc.getMember("start-line").asLong() > 0)
    }

    @Test
    fun `Loc fields accessible via rdr keys`() = withContext { ctx ->
        val line = ctx.evalBridjeForms("->: meta('foo) :rdr/loc :rdr/start-line")

        assertTrue(line.asLong() > 0)
    }
}
