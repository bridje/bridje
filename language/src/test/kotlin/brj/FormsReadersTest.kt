package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FormsReadersTest {

    private fun Path.brjLit(): String = toString().replace("\\", "\\\\")

    @Test
    fun `less-than-minus-str reads a single symbol form`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.forms.str1
              require:
                brj: as(forms, f)

            def: result f/<-str("foo")
        """.trimIndent()).getMember("result")

        assertTrue(result.hasArrayElements())
        assertEquals(1L, result.arraySize)
        assertEquals("SymbolForm", result.getArrayElement(0).metaObject.metaSimpleName)
        assertEquals("foo", result.getArrayElement(0).toString())
    }

    @Test
    fun `less-than-minus-str reads multiple top-level forms`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.forms.str2
              require:
                brj: as(forms, f)

            def: result f/<-str("1 2 3")
        """.trimIndent()).getMember("result")

        assertEquals(3L, result.arraySize)
        assertEquals("1", result.getArrayElement(0).toString())
        assertEquals("2", result.getArrayElement(1).toString())
        assertEquals("3", result.getArrayElement(2).toString())
    }

    @Test
    fun `less-than-minus-str reads a nested list`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.forms.str3
              require:
                brj: as(forms, f)

            def: result f/<-str("(foo 1 2)")
        """.trimIndent()).getMember("result")

        assertEquals(1L, result.arraySize)
        val list = result.getArrayElement(0)
        assertEquals("List", list.metaObject.metaSimpleName)
        assertEquals("(foo 1 2)", list.toString())
    }

    @Test
    fun `less-than-minus-file reads forms from disk`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("src.brj")
        Files.writeString(target, "foo\n[1 2]\n")

        val result = ctx.evalBridje("""
            ns: test.forms.file
              require:
                brj:
                  as(forms, f)
                  fs

            def: result f/<-file(fs/file("${target.brjLit()}"))
        """.trimIndent()).getMember("result")

        assertEquals(2L, result.arraySize)
        assertEquals("SymbolForm", result.getArrayElement(0).metaObject.metaSimpleName)
        assertEquals("foo", result.getArrayElement(0).toString())
        assertEquals("Vector", result.getArrayElement(1).metaObject.metaSimpleName)
        assertEquals("[1 2]", result.getArrayElement(1).toString())
    }

    @Test
    fun `less-than-minus-str on empty input returns empty vector`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.forms.empty
              require:
                brj: as(forms, f)

            def: result f/<-str("")
        """.trimIndent()).getMember("result")

        assertTrue(result.hasArrayElements())
        assertEquals(0L, result.arraySize)
    }
}
