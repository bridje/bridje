package brj

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NsIntrospectionTest {

    @Test
    fun `allNses includes brj core`() = withContext { ctx ->
        val result = ctx.evalBridje("allNses()")
        assertTrue(result.hasArrayElements())

        val names = (0 until result.arraySize).map { result.getArrayElement(it).toString() }
        assertTrue("brj.core" in names, "expected brj.core in $names")
    }

    @Test
    fun `allNses sees user-defined namespaces`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.nsone
            def: x 1
        """.trimIndent())
        ctx.evalBridje("""
            ns: test.nstwo
            def: y 2
        """.trimIndent())

        val result = ctx.evalBridje("allNses()")
        val names = (0 until result.arraySize).map { result.getArrayElement(it).toString() }
        assertTrue("test.nsone" in names)
        assertTrue("test.nstwo" in names)
    }

    @Test
    fun `nsVars lists user-defined vars`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.vars
            def: foo 1
            def: bar(x) x
        """.trimIndent())

        val result = ctx.evalBridje("nsVars(Symbol(\"test.vars\"))")
        assertTrue(result.hasArrayElements())
        assertEquals(2L, result.arraySize)

        val names = (0 until result.arraySize).map {
            val v = result.getArrayElement(it)
            assertEquals("Var", v.metaObject.metaSimpleName)
            v.getArrayElement(1).toString()
        }
        assertTrue("foo" in names)
        assertTrue("bar" in names)
    }

    @Test
    fun `nsVars each Var supports meta`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.varsmeta
            def: foo 42
        """.trimIndent())

        val metas = ctx.evalBridje("mapv(nsVars(Symbol(\"test.varsmeta\")), meta)")
        assertTrue(metas.hasArrayElements())
        assertEquals(1L, metas.arraySize)
        assertTrue(metas.getArrayElement(0).hasMember("loc"))
    }

    @Test
    fun `nsVars throws on unknown namespace`() {
        withContext { ctx ->
            assertThrows(PolyglotException::class.java) {
                ctx.evalBridje("nsVars(Symbol(\"no.such.ns\"))")
            }
        }
    }
}
