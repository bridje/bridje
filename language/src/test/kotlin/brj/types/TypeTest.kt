package brj.types

import brj.analyser.*
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
    fun `do returns type of last expression`() {
        val type = DoExpr(listOf(StringExpr("hi")), IntExpr(42)).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `let binding infers type from value`() {
        val x = LocalVar("x", 0)
        val type = LetExpr(x, IntExpr(1), LocalVarExpr(x)).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `let with unused binding`() {
        val x = LocalVar("x", 0)
        val type = LetExpr(x, IntExpr(1), StringExpr("hello")).checkType()
        assertEquals(StringType, type.base)
    }

    @Test
    fun `fn literal has FnType`() {
        val x = LocalVar("x", 0)
        val type = FnExpr("f", listOf("x"), LocalVarExpr(x), 1).checkType()
        val fnBase = type.base as FnType
        assertEquals(1, fnBase.paramTypes.size)
        assertNotNull(fnBase.returnType)
    }

    @Test
    fun `call infers return type from fn`() {
        val x = LocalVar("x", 0)
        val fn = FnExpr("f", listOf("x"), IntExpr(42), 1)
        val type = CallExpr(fn, listOf(StringExpr("hello"))).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `identity fn called with int`() {
        val x = LocalVar("x", 0)
        val identity = FnExpr("id", listOf("x"), LocalVarExpr(x), 1)
        val type = CallExpr(identity, listOf(IntExpr(1))).checkType()
        assertEquals(IntType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `higher-order fn returning fn`() {
        val x = LocalVar("x", 0)
        val y = LocalVar("y", 0)
        val inner = FnExpr("inner", listOf("y"), LocalVarExpr(x), 1)
        val outer = FnExpr("outer", listOf("x"), inner, 1)
        val type = outer.checkType()
        val outerFn = type.base as FnType
        val innerFn = outerFn.returnType.base as FnType
        assertEquals(1, innerFn.paramTypes.size)
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

    @Test
    fun `instantiate replaces type variables with fresh ones`() {
        val tv = TypeVar()
        val original = FnType(listOf(Type(NOT_NULL, tv, IntType)), Type(NOT_NULL, tv, IntType)).notNull()
        val instantiated = original.instantiate()

        val fn = instantiated.base as FnType
        assertEquals(IntType, fn.paramTypes[0].base)
        assertEquals(IntType, fn.returnType.base)

        // Type variables are fresh but internally consistent
        assertEquals(fn.paramTypes[0].tv, fn.returnType.tv)
        assertNotEquals(tv, fn.paramTypes[0].tv)
    }

    @Test
    fun `instantiate gives different vars for different originals`() {
        val tv1 = TypeVar()
        val tv2 = TypeVar()
        val original = FnType(
            listOf(Type(NOT_NULL, tv1, null), Type(NOT_NULL, tv2, null)),
            Type(NOT_NULL, tv1, null)
        ).notNull()
        val instantiated = original.instantiate()

        val fn = instantiated.base as FnType
        assertEquals(fn.paramTypes[0].tv, fn.returnType.tv)
        assertNotEquals(fn.paramTypes[0].tv, fn.paramTypes[1].tv)
    }
}
