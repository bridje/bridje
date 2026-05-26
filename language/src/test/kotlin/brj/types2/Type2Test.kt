package brj.types2

import brj.analyser.BigDecExpr
import brj.analyser.BigIntExpr
import brj.analyser.BoolExpr
import brj.analyser.DoubleExpr
import brj.analyser.FnExpr
import brj.analyser.IfExpr
import brj.analyser.IntExpr
import brj.analyser.LocalVar
import brj.analyser.LocalVarExpr
import brj.analyser.StringExpr
import brj.runtime.sym
import brj.types2.Nullability.Value.NOT_NULL
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Type2Test {

    @Test
    fun `primitive literals have their primitive types`() {
        val intT = IntExpr(1).checkType2()
        assertEquals(IntType, intT.base)
        assertEquals(NOT_NULL, intT.nullability.value)

        val doubleT = DoubleExpr(1.0).checkType2()
        assertEquals(DoubleType, doubleT.base)
        assertEquals(NOT_NULL, doubleT.nullability.value)

        val bigIntT = BigIntExpr(BigInteger.ONE).checkType2()
        assertEquals(BigIntType, bigIntT.base)
        assertEquals(NOT_NULL, bigIntT.nullability.value)

        val bigDecT = BigDecExpr(BigDecimal.ONE).checkType2()
        assertEquals(BigDecType, bigDecT.base)
        assertEquals(NOT_NULL, bigDecT.nullability.value)

        val strT = StringExpr("x").checkType2()
        assertEquals(StringType, strT.base)
        assertEquals(NOT_NULL, strT.nullability.value)

        val boolT = BoolExpr(true).checkType2()
        assertEquals(BoolType, boolT.base)
        assertEquals(NOT_NULL, boolT.nullability.value)
    }

    @Test
    fun `fn identity yields FnType with shared param and return TVs`() {
        val x = LocalVar("x".sym, 0)
        val fn = FnExpr("id".sym, listOf(x), LocalVarExpr(x), 1, emptyList(), isVariadic = false)
        val type = fn.checkType2()

        val fnBase = type.base
        assertTrue(fnBase is FnType, "expected FnType, got $fnBase")
        fnBase as FnType
        assertEquals(1, fnBase.params.size)

        val paramBase = fnBase.params[0].base
        val retBase = fnBase.ret.base
        assertTrue(paramBase is TypeVarType, "param base should be TypeVarType, got $paramBase")
        assertTrue(retBase is TypeVarType, "ret base should be TypeVarType, got $retBase")
        paramBase as TypeVarType
        retBase as TypeVarType
        assertSame(paramBase.tv, retBase.tv)

        assertSame(fnBase.params[0].nullability.nv, fnBase.ret.nullability.nv)
    }

    @Test
    fun `if with same primitive branches yields that primitive`() {
        val type = IfExpr(BoolExpr(true), IntExpr(1), IntExpr(2)).checkType2()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability.value)
    }

    @Test
    fun `if with mismatched primitive branches raises`() {
        assertThrows(TypeErrorException::class.java) {
            IfExpr(BoolExpr(true), IntExpr(1), StringExpr("a")).checkType2()
        }
    }
}
