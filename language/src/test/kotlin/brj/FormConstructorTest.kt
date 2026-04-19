package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FormConstructorTest {
    @Test
    fun `Vector creates VectorForm from quoted elements`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/Vector(['a 'b])")
        assertEquals("Vector", result.metaObject.metaSimpleName)
        assertEquals("[a b]", result.toString())
    }

    @Test
    fun `List creates ListForm from forms`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/List([f/SymbolForm(Symbol(\"if\")) f/SymbolForm(Symbol(\"true\")) f/Int(1) f/Int(2)])")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(if true 1 2)", result.toString())
    }

    @Test
    fun `Symbol creates Symbol value from string`() = withContext { ctx ->
        val result = ctx.evalBridje("Symbol(\"foo\")")
        assertEquals("Symbol", result.metaObject.metaSimpleName)
        assertEquals("foo", result.toString())
    }

    @Test
    fun `SymbolForm wraps Symbol`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/SymbolForm(Symbol(\"foo\"))")
        assertEquals("SymbolForm", result.metaObject.metaSimpleName)
        assertEquals("foo", result.toString())
    }

    @Test
    fun `Int creates IntForm from number`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/Int(42)")
        assertEquals("Int", result.metaObject.metaSimpleName)
        assertEquals("42", result.toString())
    }

    @Test
    fun `String creates StringForm`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/String(\"hello\")")
        assertEquals("String", result.metaObject.metaSimpleName)
        assertEquals("\"hello\"", result.toString())
    }

    @Test
    fun `nested form construction`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/List([f/SymbolForm(Symbol(\"let\")) f/Vector(['x f/Int(1)]) f/SymbolForm(Symbol(\"x\"))])")
        assertEquals("List", result.metaObject.metaSimpleName)
        assertEquals("(let [x 1] x)", result.toString())
    }

    @Test
    fun `Set creates SetForm`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/Set(['a 'b])")
        assertEquals("Set", result.metaObject.metaSimpleName)
        assertEquals("#{a b}", result.toString())
    }

    @Test
    fun `Record creates RecordForm`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/Record([f/SymbolForm(Symbol(\"a\")) f/Int(1)])")
        assertEquals("Record", result.metaObject.metaSimpleName)
        assertEquals("{a 1}", result.toString())
    }

    @Test
    fun `Double creates DoubleForm`() = withContext { ctx ->
        val result = ctx.evalBridjeForms("f/Double(3.14)")
        assertEquals("Double", result.metaObject.metaSimpleName)
        assertEquals("3.14", result.toString())
    }
}
