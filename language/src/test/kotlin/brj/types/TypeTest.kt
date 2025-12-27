package brj.types

import brj.*
import brj.types.Nullability.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TypeTest {

    @Test
    fun `int literal has int type`() {
        val type = IntExpr(1).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `nil has nullable unknown type`() {
        val type = NilExpr().checkType()
        assertNull(type.base)
        assertEquals(NULLABLE, type.nullability)
    }

    @Test
    fun `vector of ints`() {
        val type = VectorExpr(listOf(IntExpr(1), IntExpr(2))).checkType()
        assertEquals(NOT_NULL, type.nullability)

        val vecBase = type.base as VectorType
        assertEquals(IntType, vecBase.el.base)
    }

    @Test
    fun `vector of int and nil`() {
        val type = VectorExpr(listOf(IntExpr(1), NilExpr())).checkType()
        assertEquals(NOT_NULL, type.nullability)

        val vecBase = type.base as VectorType
        assertEquals(IntType, vecBase.el.base)
        assertEquals(NULLABLE, vecBase.el.nullability)
    }

    @Test
    fun `if with same branch types`() {
        // (if true 1 2) -> Int
        val type = IfExpr(BoolExpr(true), IntExpr(1), IntExpr(2)).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `if with int and nil`() {
        // (if true 1 nil) -> Int?
        val type = IfExpr(BoolExpr(true), IntExpr(1), NilExpr()).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NULLABLE, type.nullability)
    }

    @Test
    fun `if with vectors`() {
        // (if true [1] [nil]) -> Vector(Int?)
        val type = IfExpr(
            BoolExpr(true),
            VectorExpr(listOf(IntExpr(1))),
            VectorExpr(listOf(NilExpr()))
        ).checkType()

        val vecBase = type.base as VectorType
        assertEquals(IntType, vecBase.el.base)
        assertEquals(NULLABLE, vecBase.el.nullability)
    }
}
