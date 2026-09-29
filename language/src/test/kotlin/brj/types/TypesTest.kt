package brj.types

import brj.analyser.*
import brj.runtime.sym
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TypesTest {

    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)

    private fun fn(vararg params: LocalVar, body: ValueExpr) =
        FnExpr("f".sym, params.toList(), body, params.size, emptyList(), false)

    private fun ValueExpr.positiveType(): Type = typing().let { it.type.shown(it.bounds) }

    @Test
    fun `int literal is Int`() {
        assertEquals(IntType, IntExpr(1).positiveType())
    }

    @Test
    fun `nil is the nullable bottom`() {
        assertEquals(NothingType.nullable(), NilExpr().positiveType())
    }

    @Test
    fun `if with equal branches is that type`() {
        assertEquals(IntType, IfExpr(BoolExpr(true), IntExpr(1), IntExpr(2)).positiveType())
    }

    @Test
    fun `if joining Int and nil is Int?`() {
        assertEquals(IntType.nullable(), IfExpr(BoolExpr(true), IntExpr(1), NilExpr()).positiveType())
    }

    @Test
    fun `if joining Int and Str is a cross-kind error`() {
        assertThrows<TypeCheckException> {
            IfExpr(BoolExpr(true), IntExpr(1), StringExpr("s")).typing()
        }
    }

    @Test
    fun `if predicate must be Bool`() {
        assertThrows<TypeCheckException> {
            IfExpr(IntExpr(0), IntExpr(1), IntExpr(2)).typing()
        }
    }

    @Test
    fun `vector of Int and nil is a vector of Int?`() {
        assertEquals(VectorType(IntType.nullable()), VectorExpr(listOf(IntExpr(1), NilExpr())).positiveType())
    }

    @Test
    fun `do is its last expression`() {
        assertEquals(IntType, DoExpr(listOf(StringExpr("hi")), IntExpr(42)).positiveType())
    }

    @Test
    fun `let binding flows into its use`() {
        val x = lv("x")
        assertEquals(IntType, LetExpr(x, IntExpr(1), LocalVarExpr(x)).positiveType())
    }

    @Test
    fun `let with an unused binding still types the binding`() {
        val x = lv("x")
        assertThrows<TypeCheckException> {
            LetExpr(x, IfExpr(IntExpr(0), IntExpr(1), IntExpr(2)), StringExpr("s")).typing()
        }
    }

    @Test
    fun `identity fn shares one variable between param and result`() {
        val x = lv("x")
        val typing = fn(x, body = LocalVarExpr(x)).typing()
        val t = typing.type.fn
        assertEquals(t.paramTypes.single(), t.returnType)
        assertTrue(typing.bounds.lower(t.returnType.tv).isEmpty)
    }

    @Test
    fun `fn returning param or literal has a variable with two lower bounds`() {
        val x = lv("x")
        val p = lv("p", 1)
        val typing = fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), IntExpr(1))).typing()
        val t = typing.type.fn
        val env = typing.bounds

        val (pT, xT) = t.paramTypes
        assertEquals(BoolType, env.upper(pT.tv).concrete)

        val result = t.returnType.tv
        assertEquals(IntType, env.lower(result).concrete)
        assertEquals(setOf(xT.tv), env.lower(result).tvs)
        assertTrue(env.upper(xT.tv).tvs.containsKey(result))
    }

    @Test
    fun `calling identity with an Int gives Int`() {
        val x = lv("x")
        assertEquals(IntType, CallExpr(fn(x, body = LocalVarExpr(x)), listOf(IntExpr(1))).positiveType())
    }

    @Test
    fun `call arity mismatch is an error`() {
        val x = lv("x")
        assertThrows<TypeCheckException> {
            CallExpr(fn(x, body = LocalVarExpr(x)), listOf(IntExpr(1), IntExpr(2))).typing()
        }
    }

    @Test
    fun `a nullable argument reaching a non-null demand is an error`() {
        // ((fn (x) (if x 1 2)) (if p 1 nil)): x is demanded Bool and given Int?.
        val x = lv("x")
        val f = fn(x, body = IfExpr(LocalVarExpr(x), IntExpr(1), IntExpr(2)))
        assertThrows<TypeCheckException> {
            CallExpr(f, listOf(IfExpr(BoolExpr(true), IntExpr(1), NilExpr()))).typing()
        }
    }

    @Test
    fun `nil flowing through a param widens the result to nullable`() {
        val x = lv("x")
        val p = lv("p", 1)
        val f = fn(p, x, body = IfExpr(LocalVarExpr(p), LocalVarExpr(x), IntExpr(1)))
        assertEquals(IntType.nullable(), CallExpr(f, listOf(BoolExpr(true), NilExpr())).positiveType())
    }

    @Test
    fun `two uses of one local meet their demands`() {
        // (let [x 1] (if x x x)) — x demanded Bool by the predicate, given Int.
        val x = lv("x")
        assertThrows<TypeCheckException> {
            LetExpr(x, IntExpr(1), IfExpr(LocalVarExpr(x), LocalVarExpr(x), LocalVarExpr(x))).typing()
        }
    }

    @Test
    fun `a typing is a value the solver does not mutate`() {
        val x = lv("x")
        val typing = fn(x, body = LocalVarExpr(x)).typing()
        val param = typing.type.fn.paramTypes.single()
        val after = typing.bounds.constrain(IntType, param)
        assertEquals(IntType, after.lower(param.tv).concrete)
        assertTrue(typing.bounds.lower(param.tv).isEmpty)
    }

    @Test
    fun `solver terminates on a two-cycle and propagates through it`() {
        val a = freshVar()
        val b = freshVar()
        val env = emptyMap<TypeVar, Bounds>()
            .constrain(a, b)
            .constrain(b, a)
            .constrain(IntType, a)
        assertEquals(IntType, env.lower(a.tv).concrete)
        assertEquals(IntType, env.lower(b.tv).concrete)
        assertThrows<TypeCheckException> { env.constrain(b, StrType) }
    }

    @Test
    fun `solver propagates a lower bound round a three-cycle`() {
        val a = freshVar()
        val b = freshVar()
        val c = freshVar()
        val env = emptyMap<TypeVar, Bounds>()
            .constrain(a, b)
            .constrain(b, c)
            .constrain(c, a)
            .constrain(IntType, b)
        assertEquals(IntType, env.lower(a.tv).concrete)
        assertEquals(IntType, env.lower(b.tv).concrete)
        assertEquals(IntType, env.lower(c.tv).concrete)
    }

    @Test
    fun `a recursive bound terminates and is rejected rather than looping`() {
        // a ≤ [a] with [Int] ≤ a implies Int ≤ a, and Int ∨ [Int] is cross-kind.
        val a = freshVar()
        val env = emptyMap<TypeVar, Bounds>().constrain(a, VectorType(a))
        assertThrows<TypeCheckException> { env.constrain(VectorType(IntType), a) }
    }

    @Test
    fun `same-kind structured lower bounds merge through a fresh variable`() {
        val a = freshVar()
        val env = emptyMap<TypeVar, Bounds>()
            .constrain(VectorType(IntType), a)
            .constrain(VectorType(NothingType.nullable()), a)
        assertEquals(VectorType(IntType.nullable()), a.shown(env))
    }

    @Test
    fun `a nullable lower bound against a non-null upper bound is an error`() {
        val a = freshVar()
        val env = emptyMap<TypeVar, Bounds>()
            .constrain(NothingType.nullable(), a)
            .constrain(IntType, a)
        assertThrows<TypeCheckException> { env.constrain(a, IntType) }
    }

    @Test
    fun `upper bounds meet across kinds to Nothing`() {
        val a = freshVar()
        val env = emptyMap<TypeVar, Bounds>()
            .constrain(a, IntType)
            .constrain(a, StrType)
        assertEquals(NothingType, env.upper(a.tv).concrete)
        assertThrows<TypeCheckException> { env.constrain(IntType, a) }
    }

    @Test
    fun `a variable below a nullable variable passes its nil no further`() {
        val a = freshVar()
        val b = freshVar()
        val edgeFirst = emptyMap<TypeVar, Bounds>()
            .constrain(a, b.nullable())
            .constrain(NothingType.nullable(), a)
            .constrain(IntType, a)
        val boundsFirst = emptyMap<TypeVar, Bounds>()
            .constrain(NothingType.nullable(), a)
            .constrain(IntType, a)
            .constrain(a, b.nullable())
        for (env in listOf(edgeFirst, boundsFirst)) {
            assertEquals(IntType, env.lower(b.tv).concrete)
            env.constrain(b, IntType)
        }
    }
}
