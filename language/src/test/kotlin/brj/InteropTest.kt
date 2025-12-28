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
        val result = ctx.evalBridje("java:lang:String(\"hello\")")
        assertTrue(result.isString, "Result should be a String")
        assertEquals("hello", result.asString())
    }

    @Test
    fun `constructor as first-class value can be invoked`() = withContext { ctx ->
        val constructor = ctx.evalBridje("java:lang:String")
        assertTrue(constructor.canInstantiate(), "Constructor should be instantiable")
        val result = constructor.newInstance("world")
        assertTrue(result.isString, "Result should be a String")
        assertEquals("world", result.asString())
    }

    @Test
    fun `returns first-class constructor value`() = withContext { ctx ->
        val result = ctx.evalBridje("""
            let: [ctor java:lang:String]
              ctor("from-constructor")
        """.trimIndent())
        assertTrue(result.isString, "Result should be a String")
        assertEquals("from-constructor", result.asString())
    }

    @Test
    fun `evaluating package class returns Truffle object that can be instantiated via Graal API`() = withContext { ctx ->
        val hostClass = ctx.evalBridje("java:lang:String")

        assertTrue(hostClass.canInstantiate())
        val instance = hostClass.newInstance("via-instantiate")
        assertTrue(instance.isString)
        assertEquals("via-instantiate", instance.asString())

        assertTrue(hostClass.canExecute())
        val instance2 = hostClass.execute("via-execute")
        assertTrue(instance2.isString)
        assertEquals("via-execute", instance2.asString())
    }
}
