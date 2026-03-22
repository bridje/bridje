package brj.types

import brj.*
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
    fun `record literal has record type`() {
        val type = RecordExpr(listOf("name" to StringExpr("Alice"), "age" to IntExpr(30))).checkType()
        assertEquals(RecordType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `empty record has record type`() {
        val type = RecordExpr(emptyList()).checkType()
        assertEquals(RecordType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `record set has record type`() {
        val x = LocalVar("x", 0)
        val type = RecordSetExpr(LocalVarExpr(x), "name", StringExpr("Alice")).checkType()
        assertEquals(RecordType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `set literal has set type`() {
        val type = SetExpr(listOf(IntExpr(1), IntExpr(2))).checkType()
        assertEquals(SetType, type.base)
        assertEquals(NOT_NULL, type.nullability)
    }

    @Test
    fun `case with nil pattern`() {
        val type = CaseExpr(
            NilExpr(),
            listOf(CaseBranch(NilPattern(), IntExpr(42)))
        ).checkType()
        assertEquals(IntType, type.base)
    }

    @Test
    fun `case with default pattern`() {
        val type = CaseExpr(
            IntExpr(1),
            listOf(CaseBranch(DefaultPattern(), StringExpr("hello")))
        ).checkType()
        assertEquals(StringType, type.base)
    }

    @Test
    fun `case with catchall binding`() {
        val x = LocalVar("x", 0)
        val type = CaseExpr(
            IntExpr(1),
            listOf(CaseBranch(CatchAllBindingPattern(x), LocalVarExpr(x)))
        ).checkType()
        assertEquals(IntType, type.base)
    }

    @Test
    fun `case joins branch types`() {
        val type = CaseExpr(
            NilExpr(),
            listOf(
                CaseBranch(NilPattern(), IntExpr(1)),
                CaseBranch(DefaultPattern(), IntExpr(2)),
            )
        ).checkType()
        assertEquals(IntType, type.base)
    }

    @Test
    fun `case with tag pattern bindings`() {
        val x = LocalVar("x", 0)
        val scrutinee = LocalVar("s", 1)
        val type = CaseExpr(
            LocalVarExpr(scrutinee),
            listOf(CaseBranch(TagPattern("Just", listOf(x)), LocalVarExpr(x)))
        ).checkType()
        assertNotNull(type)
    }

    @Test
    fun `global var reads type from GlobalVar`() {
        val tv = TypeVar()
        val fnType = FnType(listOf(Type(NOT_NULL, tv, null)), Type(NOT_NULL, tv, null)).notNull()
        val gv = GlobalVar("id", null, type = fnType)
        val type = GlobalVarExpr(gv).checkType()
        val fn = type.base as FnType
        assertEquals(fn.paramTypes[0].tv, fn.returnType.tv)
        assertNotEquals(tv, fn.paramTypes[0].tv)
    }

    @Test
    fun `global var with no type returns fresh`() {
        val type = GlobalVarExpr(GlobalVar("x", 42)).checkType()
        assertNotNull(type)
    }

    @Test
    fun `quote has form type`() {
        val type = QuoteExpr(IntForm(1)).checkType()
        assertEquals(FormType, type.base)
        assertEquals(NOT_NULL, type.nullability)
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
