package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BytesTest {

    private fun Path.brjLit(): String = toString().replace("\\", "\\\\")

    @Test
    fun `Bytes count returns the byte length`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.bytes.count
              require:
                brj:
                  as(bytes, by)
            def: result by/count(by/<-str("hello"))
        """.trimIndent()).getMember("result")
        assertEquals(5L, result.asLong())
    }

    @Test
    fun `Bytes nth widens to Int`() = withContext { ctx ->
        // "ABC" -> bytes [65, 66, 67]
        val ns = ctx.evalBridje("""
            ns: test.bytes.nth
              require:
                brj:
                  as(bytes, by)
            def: bs by/<-str("ABC")
            def: b0 by/nth(bs, 0)
            def: b1 by/nth(bs, 1)
            def: b2 by/nth(bs, 2)
        """.trimIndent())
        assertEquals(65L, ns.getMember("b0").asLong())
        assertEquals(66L, ns.getMember("b1").asLong())
        assertEquals(67L, ns.getMember("b2").asLong())
    }

    @Test
    fun `Bytes nth returns unsigned byte values`() = withContext { ctx ->
        // "é" in UTF-8 is [0xC3, 0xA9] = [195, 169].
        // Sign-extension would return -61 and -87 respectively; unsigned returns 195 and 169.
        val ns = ctx.evalBridje("""
            ns: test.bytes.nth.unsigned
              require:
                brj:
                  as(bytes, by)
            def: bs by/<-str("é")
            def: b0 by/nth(bs, 0)
            def: b1 by/nth(bs, 1)
        """.trimIndent())
        assertEquals(195L, ns.getMember("b0").asLong())
        assertEquals(169L, ns.getMember("b1").asLong())
    }

    @Test
    fun `Str Bytes roundtrip preserves ASCII`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.bytes.roundtrip.ascii
              require:
                brj:
                  as(bytes, by)
                  as(str, s)
            def: result s/<-bytes(by/<-str("hello, world"))
        """.trimIndent()).getMember("result")
        assertEquals("hello, world", result.asString())
    }

    @Test
    fun `Str Bytes roundtrip preserves multibyte UTF-8`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            ns: test.bytes.roundtrip.utf8
              require:
                brj:
                  as(bytes, by)
                  as(str, s)
            def: result s/<-bytes(by/<-str("héllo"))
        """.trimIndent()).getMember("result")
        assertEquals("héllo", result.asString())
    }

    @Test
    fun `multibyte character takes multiple bytes`() = withContext { ctx ->
        // "é" in UTF-8 is two bytes (0xC3 0xA9).
        val result = ctx.evalBridje("""
            ns: test.bytes.utf8len
              require:
                brj:
                  as(bytes, by)
            def: result by/count(by/<-str("é"))
        """.trimIndent()).getMember("result")
        assertEquals(2L, result.asLong())
    }

    @Test
    fun `fs read-bytes returns file contents as Bytes`(@TempDir dir: Path) = withContext { ctx ->
        val target = dir.resolve("payload.bin")
        Files.write(target, byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)) // "Hello"

        val ns = ctx.evalBridje("""
            ns: test.bytes.fs.readbytes
              require:
                brj:
                  fs
                  as(bytes, by)
                  as(str, s)
            def: bs fs/<-bytes(fs/file("${target.brjLit()}"))
            def: size by/count(bs)
            def: first by/nth(bs, 0)
            def: text s/<-bytes(bs)
        """.trimIndent())

        assertEquals(5L, ns.getMember("size").asLong())
        assertEquals(0x48L, ns.getMember("first").asLong())
        assertEquals("Hello", ns.getMember("text").asString())
    }
}
