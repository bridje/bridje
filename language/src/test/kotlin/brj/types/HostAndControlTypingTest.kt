package brj.types

import brj.analyser.*
import brj.runtime.QSymbol
import brj.runtime.sym
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HostAndControlTypingTest {

    private val arrayList = "java.util.ArrayList"
    private val linkedList = "java.util.LinkedList"
    private val list = "java.util.List"
    private val iterable = "java.lang.Iterable"
    private val stringBuilder = "java.lang.StringBuilder"
    private val map = "java.util.Map"

    private fun host(cls: String, vararg args: Type) = HostType(cls, args.toList())
    private fun env() = emptyMap<TypeVar, Bounds>()

    @Test
    fun `a host class is a subtype of its interfaces with arguments carried up`() {
        val a = freshVar()
        val env = env().constrain(host(arrayList, IntType), host(iterable, a))
        assertEquals(IntType, env.lower(a.tv).concrete)
        assertEquals(IntType, env.upper(a.tv).concrete)
    }

    @Test
    fun `unrelated host classes are rejected`() {
        assertThrows<TypeCheckException> { env().constrain(host(arrayList, IntType), host(stringBuilder)) }
    }

    @Test
    fun `an erased host type is compatible with an applied one`() {
        val a = freshVar()
        env().constrain(host(arrayList), host(list, a))
        env().constrain(host(arrayList, IntType), host(list))
    }

    @Test
    fun `host arguments are invariant`() {
        env().constrain(host(arrayList, IntType), host(arrayList, IntType))
        assertThrows<TypeCheckException> { env().constrain(host(arrayList, IntType), host(arrayList, IntType.nullable())) }
    }

    @Test
    fun `host classes join along the class chain, and otherwise as Iterables where both are`() {
        val a = freshVar()
        val env = env().constrain(host(arrayList, IntType), a).constrain(host(list, IntType), a)
        assertEquals(host(list, IntType), a.shown(env))
        val b = freshVar()
        val envB = env().constrain(host(arrayList, IntType), b).constrain(host(linkedList, IntType), b)
        assertEquals(IterableType(IntType), b.shown(envB))
        assertThrows<TypeCheckException> {
            val c = freshVar()
            env().constrain(host(arrayList, IntType), c).constrain(host(stringBuilder), c)
        }
    }

    @Test
    fun `a host class demanded as an Iterable of another element is an error`() {
        val a = freshVar()
        val env = env().constrain(a, IterableType(StrType))
        assertThrows<TypeCheckException> { env.constrain(a, host(arrayList, IntType)) }
    }

    @Test
    fun `a raw host class demanded beside an applied one keeps the applied arguments`() {
        val a = freshVar()
        val env = env().constrain(a, host(arrayList, IntType)).constrain(a, host(arrayList))
        assertThrows<TypeCheckException> { env.constrain(host(arrayList, StrType), a) }
    }

    @Test
    fun `a vector and a set join to an Iterable`() {
        val a = freshVar()
        val env = env().constrain(VectorType(IntType), a).constrain(SetType(IntType), a)
        assertEquals(IterableType(IntType), env.lower(a.tv).concrete.shown(env))
    }

    @Test
    fun `an iterable host class and a vector join to an Iterable`() {
        val a = freshVar()
        val env = env().constrain(host(arrayList, IntType), a).constrain(VectorType(IntType), a)
        assertEquals(IterableType(IntType), env.lower(a.tv).concrete.shown(env))
    }

    @Test
    fun `vectors, sets and iterable host classes are Iterable`() {
        val a = freshVar()
        val b = freshVar()
        val env = env()
            .constrain(VectorType(IntType), IterableType(a))
            .constrain(host(arrayList, StrType), IterableType(b))
        assertEquals(IntType, env.lower(a.tv).concrete)
        assertEquals(StrType, env.lower(b.tv).concrete)
        assertThrows<TypeCheckException> { env().constrain(host(stringBuilder), IterableType(freshVar())) }
    }

    @Test
    fun `a polymorphic host method with invariant arguments terminates and types`() {
        // decl: [k, v] Map/assoc(Map(k, v), k, v) Map(k, v), applied to (Map(k1, v1), Int, Int) — the #128 shape.
        val k = freshVar()
        val v = freshVar()
        val k1 = freshVar()
        val v1 = freshVar()
        val result = freshVar()
        val assoc = FnType(listOf(host(map, k, v), k, v), host(map, k, v))
        val env = env().constrain(assoc, FnType(listOf(host(map, k1, v1), IntType, IntType), result))
        assertEquals(host(map, IntType, IntType), result.shown(env))
    }

    @Test
    fun `a function taking a trailing options record may be called without it`() {
        val r = freshVar()
        val env = env().constrain(FnType(listOf(IntType, RecordType(emptySet())), StrType), FnType(listOf(IntType), r))
        assertEquals(StrType, env.lower(r.tv).concrete)
        assertThrows<TypeCheckException> {
            env().constrain(FnType(listOf(IntType, StrType), StrType), FnType(listOf(IntType), freshVar()))
        }
    }

    @Test
    fun `a function called without its last parameter must take the empty record there`() {
        val message = QSymbol(ns, "message".sym)
        assertThrows<TypeCheckException> {
            env().constrain(FnType(listOf(RecordType(setOf(message))), StrType), FnType(emptyList(), freshVar()))
        }
    }

    @Test
    fun `whether a parameter may be left off does not depend on when its demands arrive`() {
        val p = freshVar()
        val env = env().constrain(FnType(listOf(IntType, p), StrType), FnType(listOf(IntType), freshVar()))
        assertThrows<TypeCheckException> { env.constrain(p, IntType) }
    }

    private val ns = "t".sym
    private val notFound = Any()
    private val ctx = TypeCtx(
        keyTypes = { if (it.name.name == "message") KeyType(StrType) else null },
        tagsByValue = mapOf(notFound to TagInfo(TagRef(ns, "NotFound".sym), null, emptyList(), Payload.Record(setOf(QSymbol(ns, "message".sym))))),
    )

    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)
    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.shown(it.bounds, ctx) }

    @Test
    fun `try joins the body with every handler and binds the anomaly payload`() {
        val e = lv("e")
        val t = TryCatchExpr(IntExpr(1), listOf(CaseBranch(TagPattern(notFound, PayloadBinding.Whole(e)), IntExpr(0))), null)
        assertEquals(IntType, t.positiveType())

        val readsPayload = TryCatchExpr(
            IntExpr(1),
            listOf(CaseBranch(TagPattern(notFound, PayloadBinding.Whole(e)), CallExpr(
                GlobalVarExpr(brj.GlobalVar(ns, "message".sym, brj.runtime.BridjeKey(ns, "message".sym))),
                listOf(LocalVarExpr(e)),
            ))),
            null,
        )
        assertThrows<TypeCheckException> { readsPayload.typing(ctx) }
    }

    @Test
    fun `loop binding is the join of its initial value and what recur sends`() {
        val x = lv("x")
        assertEquals(IntType, LoopExpr(listOf(x to IntExpr(1)), LocalVarExpr(x)).positiveType())

        val p = lv("p", 1)
        val looped = LoopExpr(
            listOf(x to IntExpr(1)),
            IfExpr(LocalVarExpr(p), RecurExpr(listOf(x), listOf(IntExpr(2))), LocalVarExpr(x)),
        )
        val typing = FnExpr("f".sym, listOf(p), looped, 1, emptyList(), false).typing(ctx)
        assertEquals(IntType, typing.type.fn.returnType.shown(typing.bounds, ctx))

        // A binding nobody reads joins nothing; one that is read must join what recur sends it.
        LoopExpr(listOf(x to IntExpr(1)), RecurExpr(listOf(x), listOf(StringExpr("s")))).typing(ctx)
        assertThrows<TypeCheckException> {
            LoopExpr(
                listOf(x to IntExpr(1)),
                IfExpr(LocalVarExpr(p), RecurExpr(listOf(x), listOf(StringExpr("s"))), LocalVarExpr(x)),
            ).typing(ctx)
        }
    }
}
