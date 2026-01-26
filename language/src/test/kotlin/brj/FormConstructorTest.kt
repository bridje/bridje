package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FormConstructorTest {
    @Test
    fun `Vector creates VectorForm from quoted elements`() = withContext { ctx ->
        val result = ctx.evalBridje("Vector(['a 'b])")
        assertEquals("Vector", result.metaObject.metaSimpleName)
        assertEquals("[a b]", result.toString())
    }

    @Test
    fun `List creates ListForm from forms`() = withContext { ctx ->
        val result = ctx.evalBridje("List([Symbol(\"if\") Symbol(\"true\") Int(1) Int(2)])")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(if true 1 2)", result.toString())
    }

    @Test
    fun `Symbol creates SymbolForm from string`() = withContext { ctx ->
        val result = ctx.evalBridje("Symbol(\"foo\")")
        assertEquals("Symbol", result.metaObject.metaSimpleName)
        assertEquals("foo", result.toString())
    }

    @Test
    fun `Int creates IntForm from number`() = withContext { ctx ->
        val result = ctx.evalBridje("Int(42)")
        assertEquals("Int", result.metaObject.metaSimpleName)
        assertEquals("42", result.toString())
    }

    @Test
    fun `String creates StringForm`() = withContext { ctx ->
        val result = ctx.evalBridje("String(\"hello\")")
        assertEquals("String", result.metaObject.metaSimpleName)
        assertEquals("\"hello\"", result.toString())
    }

    @Test
    fun `nested form construction`() = withContext { ctx ->
        val result = ctx.evalBridje("List([Symbol(\"let\") Vector(['x Int(1)]) Symbol(\"x\")])")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(let [x 1] x)", result.toString())
    }

    @Test
    fun `Set creates SetForm`() = withContext { ctx ->
        val result = ctx.evalBridje("Set(['a 'b])")
        assertEquals("Set", result.metaObject.metaSimpleName)
        assertEquals("#{a b}", result.toString())
    }

    @Test
    fun `Record creates RecordForm`() = withContext { ctx ->
        val result = ctx.evalBridje("Record([Symbol(\"a\") Int(1)])")
        assertEquals("Record", result.metaObject.metaSimpleName)
        assertEquals("{a 1}", result.toString())
    }

    @Test
    fun `Double creates DoubleForm`() = withContext { ctx ->
        val result = ctx.evalBridje("Double(3.14)")
        assertEquals("Double", result.metaObject.metaSimpleName)
        assertEquals("3.14", result.toString())
    }
}
