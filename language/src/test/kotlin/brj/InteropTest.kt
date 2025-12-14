package brj

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InteropTest {
    @Test
    fun `calls java static method via qualified symbol`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val result = ctx.eval("bridje", "java:time:Instant/now()")
                    assertTrue(result.isInstant, "Result should be an Instant")
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `instantiates java object with constructor call`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val result = ctx.eval("bridje", "java:lang:String/new(\"hello\")")
                    assertTrue(result.isString, "Result should be a String")
                    assertEquals("hello", result.asString())
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `instantiates java object with multiple constructor arguments`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val result = ctx.eval("bridje", "java:lang:StringBuilder/new(\"test\")")
                    assertTrue(result.hasMembers(), "Result should be a StringBuilder")
                    assertEquals("test", result.invokeMember("toString").asString())
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `constructor as first-class value can be invoked`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val constructor = ctx.eval("bridje", "java:lang:String/new")
                    assertTrue(constructor.canInstantiate(), "Constructor should be instantiable")
                    val result = constructor.newInstance("world")
                    assertTrue(result.isString, "Result should be a String")
                    assertEquals("world", result.asString())
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `returns first-class constructor value and invokes through Graal API`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val code = """
                        let: [ctor java:lang:String/new]
                          ctor("from-constructor")
                    """.trimIndent()
                    val result = ctx.eval("bridje", code)
                    assertTrue(result.isString, "Result should be a String")
                    assertEquals("from-constructor", result.asString())
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `evaluating package class returns Truffle object that can be instantiated via Graal API`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val hostClass = ctx.eval("bridje", "java:lang:String")
                    assertTrue(hostClass.canInstantiate(), "Host class should be instantiable")
                    val instance = hostClass.newInstance("via-graal")
                    assertTrue(instance.isString, "Instance should be a String")
                    assertEquals("via-graal", instance.asString())
                } finally {
                    ctx.leave()
                }
            }
    }

    @Test
    fun `evaluating package class allows invoking static methods via Graal API`() {
        Context.newBuilder()
            .allowAllAccess(true)
            .logHandler(System.err)
            .build()
            .use { ctx ->
                try {
                    ctx.enter()
                    val instantClass = ctx.eval("bridje", "java:time:Instant")
                    assertTrue(instantClass.canInvokeMember("now"), "Should be able to invoke static method")
                    val result = instantClass.invokeMember("now")
                    assertTrue(result.isInstant, "Result should be an Instant")
                } finally {
                    ctx.leave()
                }
            }
    }
}
