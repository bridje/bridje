package brj

import brj.JavaImportEmitter.emitJavaImport
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.ValueExprEmitter.Companion.evalValueExpr
import brj.VariantEmitter.emitVariantKey
import brj.analyser.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
internal class TruffleEmitterTest {
    private val ctx = Context.newBuilder("brj")
        .allowAllAccess(true)
        .build()

    @BeforeAll
    internal fun setUp() {
        ctx.enter()
        ctx.initialize("brj")
    }

    @AfterAll
    internal fun tearDown() {
        ctx.leave()
    }

    @Test
    internal fun `variant introduction and elimination`() {
        val variantKey = VariantKey(mkQSym(":user/Foo"), emptyList(), listOf(IntType))
        val variantKey2 = VariantKey(mkQSym(":user/Foo2"), emptyList(), listOf(IntType))
        val constructor = emitVariantKey(variantKey2)
        val localVar = LocalVar(mkSym("a"))

        assertEquals(54L,
            evalValueExpr(
                CaseExpr(
                    CallExpr(GlobalVarExpr(VariantKeyVar(variantKey2, constructor)), null, listOf(IntExpr(54))),
                    listOf(
                        CaseClause(variantKey, listOf(localVar), IntExpr(12)),
                        CaseClause(variantKey2, listOf(localVar), LocalVarExpr(localVar))),
                    IntExpr(43))))
    }

    @Test
    internal fun `record introduction and elimination`() {
        val count = mkQSym(":user/count")
        val message = mkQSym(":user/message")

        val countKey = RecordKey(count, emptyList(), IntType)
        val messageKey = RecordKey(message, emptyList(), StringType)

        val record = Value.asValue(evalValueExpr(
            RecordExpr(listOf(
                RecordEntry(countKey, IntExpr(42)),
                RecordEntry(messageKey, StringExpr("Hello world!"))))))

        assertEquals(setOf(count.toString(), message.toString()), record.memberKeys)
        assertEquals(42L, record.getMember(count.toString()).asLong())
        assertEquals("Hello world!", record.getMember(message.toString()).asString())

        assertEquals(42L, Value.asValue(RecordEmitter.emitRecordKey(countKey)).execute(record).asLong())
    }

    @Test
    internal fun `java interop`() {
        val fn = Value.asValue(emitJavaImport(JavaImport(Foo::class.java, mkQSym("Foo/plus"), Type(FnType(listOf(IntType, IntType), BoolType)))))
        assertEquals(5, fn.execute(3, 2).asLong())
    }
}