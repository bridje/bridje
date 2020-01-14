package brj.reader

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.TypeLiteral
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

private val readFormsSource = Source.newBuilder("brj", "read-forms", "<read-forms>").internal(true).build()

internal fun readForms(s: String, ctx: Context = Context.getCurrent()) = ctx.eval(readFormsSource).execute(s).`as`(object : TypeLiteral<List<Form>>() {})

internal class ReaderTest {
    companion object {
        lateinit var ctx: Context

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            ctx = Context.newBuilder().allowAllAccess(true).build().also { it.enter() }
        }

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            ctx.close()
        }
    }

    @Test
    internal fun `test quoting`() {
        assertEquals(
            listOf("(:brj.forms/VectorForm [(:brj.forms/BooleanForm true) (:brj.forms/SymbolForm 'foo)])"),
            readForms("'[true foo]").map(Any::toString))
    }
}

