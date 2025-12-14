package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InteropTest {
    @Test
    fun `calls java static method via qualified symbol`() = withContext { ctx ->
        val result = ctx.evalBridje("java:time:Instant/now()")
        assertTrue(result.isInstant, "Result should be an Instant")
    }

    @Test
    fun `instantiates java object with constructor call`() = withContext { ctx ->
        val result = ctx.evalBridje("java:lang:String/new(\"hello\")")
        assertTrue(result.isString, "Result should be a String")
        assertEquals("hello", result.asString())
    }

    @Test
    fun `instantiates java object with multiple constructor arguments`() = withContext { ctx ->
        val result = ctx.evalBridje("java:lang:StringBuilder/new(\"test\")")
        assertTrue(result.hasMembers(), "Result should be a StringBuilder")
        assertEquals("test", result.invokeMember("toString").asString())
    }

    @Test
    fun `constructor as first-class value can be invoked`() = withContext { ctx ->
        val constructor = ctx.evalBridje("java:lang:String/new")
        assertTrue(constructor.canInstantiate(), "Constructor should be instantiable")
        val result = constructor.newInstance("world")
        assertTrue(result.isString, "Result should be a String")
        assertEquals("world", result.asString())
    }

    @Test
    fun `returns first-class constructor value and invokes through Graal API`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [ctor java:lang:String/new]
              ctor("from-constructor")
        """.trimIndent())
        assertTrue(result.isString, "Result should be a String")
        assertEquals("from-constructor", result.asString())
    }

    @Test
    fun `evaluating package class returns Truffle object that can be instantiated via Graal API`() = withContext { ctx ->
        val hostClass = ctx.evalBridje("java:lang:String")
        assertTrue(hostClass.canInstantiate(), "Host class should be instantiable")
        val instance = hostClass.newInstance("via-graal")
        assertTrue(instance.isString, "Instance should be a String")
        assertEquals("via-graal", instance.asString())
    }

    @Test
    fun `evaluating package class allows invoking static methods via Graal API`() = withContext { ctx ->
        val instantClass = ctx.evalBridje("java:time:Instant")
        assertTrue(instantClass.canInvokeMember("now"), "Should be able to invoke static method")
        val result = instantClass.invokeMember("now")
        assertTrue(result.isInstant, "Result should be an Instant")
    }
}
