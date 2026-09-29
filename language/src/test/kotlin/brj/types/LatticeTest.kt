package brj.types

import brj.runtime.QSymbol
import brj.runtime.sym
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// Joins and meets agree with subtyping: a join is above both sides, a meet below both, and where one side is
// below the other the join is no higher than it and the meet no lower. The samples span the collection, host
// and record kinds, records with a key of type variables, open, closed and optional, and named ones.
class LatticeTest {
    private val ns = "t".sym
    private fun keys(vararg names: String) = names.map { QSymbol(ns, it.sym) }.toSet()
    private fun host(cls: String, vararg args: Type) = HostType(cls, args.toList())

    private val arrayList = "java.util.ArrayList"
    private val linkedList = "java.util.LinkedList"

    private val bases: List<Type> = listOf(
        NothingType, IntType, StrType, BoolType,
        FnType(listOf(IntType), IntType), FnType(listOf(StrType), IntType), FnType(listOf(IntType), StrType),
        VectorType(IntType), VectorType(StrType), VectorType(RecordType(keys("a", "b"))), SetType(IntType),
        IterableType(IntType), IterableType(StrType), IterableType(RecordType(keys("a"))), IteratorType(IntType),
        host(arrayList, IntType), host(arrayList, StrType), host(arrayList), host(linkedList, IntType),
        host("java.util.List", IntType), host("java.lang.Iterable", IntType), host("java.util.Iterator", IntType),
        host("java.lang.StringBuilder"),
        RecordType(emptySet()), RecordType(keys("a")), RecordType(keys("a", "b")), RecordType(keys("b")),
    )

    // Records with a polymorphic key, `[a] .v a`, a generic tag over it and an enum with a nullary variant.
    private val v = QSymbol(ns, "v".sym)
    private val vParam = TypeVar()
    private val box = TagRef(ns, "Box".sym)
    private val some = TagRef(ns, "Some".sym)
    private val none = TagRef(ns, "None".sym)
    private val opt = EnumRef(ns, "Opt".sym)
    private val boxParam = TypeVar()
    private val someParam = TypeVar()
    private val noneParam = TypeVar()

    private val ctx = TypeCtx(
        keyTypes = { if (it == v) KeyType(listOf(vParam), vParam.type()) else KeyType(IntType) },
        tagsByValue = mapOf(
            Any() to TagInfo(box, null, listOf(boxParam), Payload.Record(setOf(v), mapOf(v to listOf(boxParam.type())))),
            Any() to TagInfo(some, opt, listOf(someParam), Payload.Record(setOf(v), mapOf(v to listOf(someParam.type())))),
            Any() to TagInfo(none, opt, listOf(noneParam), Payload.None),
        ),
        variantsByEnum = mapOf(opt to listOf(some, none)),
    )

    private fun rec(vararg slots: Pair<String, Slot>, closed: Boolean = false) =
        RecType(null, emptyList(), slots.associate { (k, s) -> QSymbol(ns, k.sym) to s }, closed)

    private val records: List<Type> = listOf(
        rec("v" to Slot(true, listOf(IntType))), rec("v" to Slot(true, listOf(IntType)), closed = true),
        rec("v" to Slot(true, listOf(StrType))), rec("v" to Slot(false, listOf(IntType))),
        rec("v" to Slot(true, listOf(IntType)), "a" to Slot(true), closed = true), rec("a" to Slot(true), closed = true),
        rec(closed = true),
        RecType(box, listOf(IntType), emptyMap()), RecType(box, listOf(IntType), emptyMap(), closed = true),
        RecType(box, listOf(StrType), emptyMap()), RecType(box, listOf(IntType), mapOf(QSymbol(ns, "a".sym) to Slot(true))),
        RecType(some, listOf(IntType), emptyMap()), RecType(none, listOf(IntType), emptyMap(), closed = true),
        RecType(opt, listOf(IntType), emptyMap()), RecType(opt, listOf(StrType), emptyMap()),
    )

    private val samples = (bases + records).let { it + it.map { t -> t.nullable() } }

    private val empty: BoundEnv = emptyMap()

    private fun BoundEnv.constrain(l: Type, u: Type) = constrain(l, u, ctx)

    private fun holds(env: BoundEnv, l: Type, u: Type) = runCatching { env.constrain(l, u) }.isSuccess

    private fun pairs() = samples.flatMap { a -> samples.map { b -> a to b } }

    @Test
    fun `a join is above both sides`() {
        for ((a, b) in pairs()) {
            val v = freshVar()
            val env = runCatching { empty.constrain(a, v).constrain(b, v) }.getOrNull() ?: continue
            val j = env.lower(v.tv).concrete
            assertTrue(holds(env, a, j), "$a ≤ $a ∨ $b = $j")
            assertTrue(holds(env, b, j), "$b ≤ $a ∨ $b = $j")
        }
    }

    @Test
    fun `a meet is below both sides`() {
        for ((a, b) in pairs()) {
            val v = freshVar()
            val env = runCatching { empty.constrain(v, a).constrain(v, b) }.getOrNull() ?: continue
            val m = env.upper(v.tv).concrete ?: fail("no upper bound for $a ∧ $b")
            assertTrue(holds(env, m, a), "$a ∧ $b = $m ≤ $a")
            assertTrue(holds(env, m, b), "$a ∧ $b = $m ≤ $b")
        }
    }

    @Test
    fun `where one side is below the other the join is no higher and the meet no lower`() {
        for ((a, b) in pairs()) {
            if (!holds(empty, a, b)) continue
            val j = freshVar()
            val joined = runCatching { empty.constrain(a, j).constrain(b, j) }
            assertTrue(joined.isSuccess, "$a ≤ $b, but $a ∨ $b fails: ${joined.exceptionOrNull()?.message}")
            val jEnv = joined.getOrThrow()
            assertTrue(holds(jEnv, jEnv.lower(j.tv).concrete, b), "$a ∨ $b = ${jEnv.lower(j.tv).concrete}, not ≤ $b")

            val m = freshVar()
            val met = runCatching { empty.constrain(m, a).constrain(m, b) }
            assertTrue(met.isSuccess, "$a ≤ $b, but $a ∧ $b fails: ${met.exceptionOrNull()?.message}")
            val mEnv = met.getOrThrow()
            assertTrue(holds(mEnv, a, mEnv.upper(m.tv).concrete!!), "$a ∧ $b = ${mEnv.upper(m.tv).concrete}, not ≥ $a")
        }
    }

    // Subtyping is a preorder: every type is below itself, and below whatever a type above it is below. A raw
    // generic class is the exception, as Java's is: compatible with any arguments, so ArrayList(Int) ≤ ArrayList
    // ≤ Iterable(Str) though ArrayList(Int) is not an Iterable(Str).
    @Test
    fun `subtyping is reflexive and transitive`() {
        fun raw(t: Type) = (t.base as? Base.Host)?.let { it.args.isEmpty() && HostTypeHierarchy.arity(it.className) > 0 } == true
        val ordered = samples.filterNot(::raw)
        val below = ordered.associateWith { a -> ordered.filter { b -> holds(empty, a, b) }.toSet() }
        for (a in ordered) assertTrue(a in below.getValue(a), "$a ≤ $a")
        for (a in ordered) for (b in below.getValue(a)) for (c in below.getValue(b)) {
            assertTrue(c in below.getValue(a), "$a ≤ $b and $b ≤ $c, but not $a ≤ $c")
        }
    }

    // Host classes off one chain are the exception: Java's interfaces give them no one least upper bound, so
    // ArrayList ∨ LinkedList is an Iterable though both are Lists.
    @Test
    fun `a join is below every upper bound of both sides, and a meet above every lower bound`() {
        val failures = mutableListOf<String>()
        fun host(t: Type) = t.base is Base.Host
        for ((a, b) in pairs()) {
            if (host(a) && host(b)) continue
            val j = freshVar()
            val jEnv = runCatching { empty.constrain(a, j).constrain(b, j) }.getOrNull()
            val m = freshVar()
            val mEnv = runCatching { empty.constrain(m, a).constrain(m, b) }.getOrNull()
            for (c in samples) {
                if (jEnv != null && holds(empty, a, c) && holds(empty, b, c) && !holds(jEnv, jEnv.lower(j.tv).concrete, c))
                    failures += "$a ∨ $b = ${jEnv.lower(j.tv).concrete}, not ≤ $c"
                if (mEnv != null && holds(empty, c, a) && holds(empty, c, b) && !holds(mEnv, c, mEnv.upper(m.tv).concrete!!))
                    failures += "$a ∧ $b = ${mEnv.upper(m.tv).concrete}, not ≥ $c"
            }
        }
        assertTrue(failures.isEmpty(), failures.distinct().joinToString("\n"))
    }
}
