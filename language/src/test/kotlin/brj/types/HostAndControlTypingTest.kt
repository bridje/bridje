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
        val a = TypeVar()
        val env = env().constrain(host(arrayList, IntType), host(iterable, a))
        assertEquals(IntType, env.lower(a).concrete)
        assertEquals(IntType, env.upper(a).concrete)
    }

    @Test
    fun `unrelated host classes are rejected`() {
        assertThrows<TypeCheckException> { env().constrain(host(arrayList, IntType), host(stringBuilder)) }
    }

    @Test
    fun `an erased host type is compatible with an applied one`() {
        val a = TypeVar()
        env().constrain(host(arrayList), host(list, a))
        env().constrain(host(arrayList, IntType), host(list))
    }

    @Test
    fun `host arguments are invariant`() {
        env().constrain(host(arrayList, IntType), host(arrayList, IntType))
        assertThrows<TypeCheckException> { env().constrain(host(arrayList, IntType), host(arrayList, NullableType(IntType))) }
    }

    @Test
    fun `host classes join along the class chain only`() {
        val a = TypeVar()
        val env = env().constrain(host(arrayList, IntType), a).constrain(host(list, IntType), a)
        assertEquals(host(list, IntType), a.positive(env))
        val b = TypeVar()
        assertThrows<TypeCheckException> {
            env().constrain(host(arrayList, IntType), b).constrain(host(linkedList, IntType), b)
        }
    }

    @Test
    fun `vectors, sets and iterable host classes are Iterable`() {
        val a = TypeVar()
        val b = TypeVar()
        val env = env()
            .constrain(VectorType(IntType), IterableType(a))
            .constrain(host(arrayList, StrType), IterableType(b))
        assertEquals(IntType, env.lower(a).concrete)
        assertEquals(StrType, env.lower(b).concrete)
        assertThrows<TypeCheckException> { env().constrain(host(stringBuilder), IterableType(TypeVar())) }
    }

    @Test
    fun `a polymorphic host method with invariant arguments terminates and types`() {
        // decl: [k, v] Map/assoc(Map(k, v), k, v) Map(k, v), applied to (Map(k1, v1), Int, Int) — the #128 shape.
        val k = TypeVar()
        val v = TypeVar()
        val k1 = TypeVar()
        val v1 = TypeVar()
        val result = TypeVar()
        val assoc = FnType(listOf(host(map, k, v), k, v), host(map, k, v))
        val env = env().constrain(assoc, FnType(listOf(host(map, k1, v1), IntType, IntType), result))
        assertEquals(host(map, IntType, IntType), result.positive(env))
    }

    @Test
    fun `a function taking a trailing options record may be called without it`() {
        val r = TypeVar()
        val env = env().constrain(FnType(listOf(IntType, RecordType(emptySet())), StrType), FnType(listOf(IntType), r))
        assertEquals(StrType, env.lower(r).concrete)
        assertThrows<TypeCheckException> {
            env().constrain(FnType(listOf(IntType, StrType), StrType), FnType(listOf(IntType), TypeVar()))
        }
    }

    private val ns = "t".sym
    private val notFound = Any()
    private val ctx = TypeCtx(
        keyTypes = { if (it.name.name == "message") StrType else null },
        tagsByValue = mapOf(notFound to TagInfo(TagRef(ns, "NotFound".sym), null, 0, Payload.Record(setOf(QSymbol(ns, "message".sym))))),
    )

    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)
    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.positive(it.bounds, ctx) }

    @Test
    fun `try joins the body with every handler and binds the anomaly payload`() {
        val e = lv("e")
        val t = TryCatchExpr(IntExpr(1), listOf(CaseBranch(TagPattern(notFound, listOf(e)), IntExpr(0))), null)
        assertEquals(IntType, t.positiveType())

        val readsPayload = TryCatchExpr(
            IntExpr(1),
            listOf(CaseBranch(TagPattern(notFound, listOf(e)), CallExpr(
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
        assertEquals(IntType, (typing.type as FnType).returnType.positive(typing.bounds, ctx))

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
