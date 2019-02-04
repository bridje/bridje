package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.ValueExprEmitter.Companion.evalValueExpr
import brj.VariantEmitter.emitVariant
import org.graalvm.polyglot.Context
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        val variantKey = VariantKey(mkQSym(":user/Foo"), null, listOf(IntType))
        val variantKey2 = VariantKey(mkQSym(":user/Foo2"), null, listOf(IntType))
        val constructor = emitVariant(variantKey2)
        val localVar = LocalVar(mkSym("a"))

        assertEquals(54L,
            evalValueExpr(
                CaseExpr(
                    CallExpr(GlobalVarExpr(VariantKeyVar(variantKey2, constructor)), listOf(IntExpr(54))),
                    listOf(
                        CaseClause(variantKey, listOf(localVar), IntExpr(12)),
                        CaseClause(variantKey2, listOf(localVar), LocalVarExpr(localVar))),
                    IntExpr(43))))
    }
}