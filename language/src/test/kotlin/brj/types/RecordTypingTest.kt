package brj.types

import brj.GlobalVar
import brj.analyser.*
import brj.runtime.BridjeKey
import brj.runtime.BridjeOptionalKey
import brj.runtime.QSymbol
import brj.runtime.sym
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RecordTypingTest {

    private val ns = "t".sym

    private val keyTypes = mapOf(
        "a" to IntType, "b" to IntType, "c" to IntType,
        "dirty" to BoolType, "name" to StrType,
    )

    private val ctx = TypeCtx(keyTypes = { key -> keyTypes[key.name.name]?.let(::KeyType) })

    private fun key(name: String) = QSymbol(ns, name.sym)
    private fun keys(vararg names: String) = names.map { key(it) }.toSet()

    // `.k` and `.?k` as the analyser produces them: global vars whose values are the key objects.
    private fun getter(name: String) =
        GlobalVarExpr(GlobalVar(ns, name.sym, BridjeKey(ns, name.sym)))

    private fun optGetter(name: String) =
        GlobalVarExpr(GlobalVar(ns, "?$name".sym, BridjeOptionalKey(BridjeKey(ns, name.sym))))

    private fun get(name: String, target: ValueExpr) = CallExpr(getter(name), listOf(target))
    private fun getOpt(name: String, target: ValueExpr) = CallExpr(optGetter(name), listOf(target))

    private fun record(vararg fields: Pair<String, ValueExpr>) = RecordExpr(fields.map { (k, v) -> key(k) to v })
    private fun with(r: ValueExpr, vararg fields: Pair<String, ValueExpr>) = RecordUpdateExpr(r, fields.map { (k, v) -> key(k) to v })

    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)

    private fun fn(vararg params: LocalVar, body: ValueExpr) =
        FnExpr("f".sym, params.toList(), body, params.size, emptyList(), false)

    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.shown(it.bounds) }
    private fun ValueExpr.shown(): String = typing(ctx).generalise().simplify(ctx).toString()

    @Test
    fun `record literal is its key set`() {
        assertEquals(RecordType(keys("a", "b")).closed(), record("a" to IntExpr(1), "b" to IntExpr(2)).positiveType())
    }

    @Test
    fun `record literal value must fit the key's declared type`() {
        assertThrows<TypeCheckException> { record("a" to StringExpr("s")).typing(ctx) }
    }

    @Test
    fun `getter reads a present key by width subtyping`() {
        assertEquals(IntType, get("a", record("a" to IntExpr(1), "b" to IntExpr(2))).positiveType())
    }

    @Test
    fun `getter on a missing key is an error`() {
        assertThrows<TypeCheckException> { get("b", record("a" to IntExpr(1))).typing(ctx) }
    }

    @Test
    fun `optional getter is total over records and nullable`() {
        assertEquals(IntType.nullable(), getOpt("b", record("a" to IntExpr(1))).positiveType())
    }

    @Test
    fun `getter on a non-record is an error`() {
        assertThrows<TypeCheckException> { get("a", IntExpr(1)).typing(ctx) }
    }

    @Test
    fun `join of two literals keeps the shared keys, and those only one carries as optional`() {
        val joined = IfExpr(BoolExpr(true), record("a" to IntExpr(1), "b" to IntExpr(2)), record("a" to IntExpr(1), "c" to IntExpr(3)))
        assertEquals("{t/.a, t/.?b, t/.?c}", joined.positiveType().toString())
        assertEquals(IntType, get("a", joined).positiveType())
        assertThrows<TypeCheckException> { get("b", joined).typing(ctx) }
        assertEquals(IntType.nullable(), getOpt("b", joined).positiveType())
    }

    @Test
    fun `with on a literal adds the key`() {
        assertEquals(RecordType(keys("a", "b")).closed(), with(record("a" to IntExpr(1)), "b" to IntExpr(2)).positiveType())
    }

    @Test
    fun `with value must fit the key's declared type`() {
        val r = lv("r")
        assertThrows<TypeCheckException> { fn(r, body = with(LocalVarExpr(r), "a" to StringExpr("s"))).typing(ctx) }
    }

    @Test
    fun `with on a variable is a record on it, and demands only that it be a record`() {
        val r = lv("r")
        val typing = fn(r, body = with(LocalVarExpr(r), "b" to IntExpr(1))).typing(ctx)
        val t = typing.type.fn
        val param = t.paramTypes.single().tv
        assertEquals(RecordType(keys("b"), param), t.returnType)
        assertEquals(RecordType(emptySet()), typing.bounds.upper(param).concrete)
    }

    @Test
    fun `reading the added key after with makes no demand on the input`() {
        val r = lv("r")
        val typing = fn(r, body = get("b", with(LocalVarExpr(r), "b" to IntExpr(1)))).typing(ctx)
        val param = typing.type.fn.paramTypes.single().tv
        assertEquals(RecordType(emptySet()), typing.bounds.upper(param).concrete)
        assertEquals(IntType, typing.type.let { it.fn.returnType.shown(typing.bounds) })
    }

    @Test
    fun `reading another key after with demands it of the input`() {
        val r = lv("r")
        val typing = fn(r, body = get("a", with(LocalVarExpr(r), "b" to IntExpr(1)))).typing(ctx)
        val param = typing.type.fn.paramTypes.single().tv
        assertEquals(RecordType(keys("a")), typing.bounds.upper(param).concrete)
    }

    @Test
    fun `if returning with or the input absorbs to the input`() {
        // (fn (p r) (if p (with r .dirty true) r)) : the two uses of r are one variable once simplified,
        // and {.dirty & r} ∨ r is r.
        val r = lv("r")
        val p = lv("p", 1)
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "dirty" to BoolExpr(true)), LocalVarExpr(r))
        assertEquals("[a] Fn([Bool, {& a}] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `if returning the input or a literal keeps both as lower bounds`() {
        val r = lv("r")
        val p = lv("p", 1)
        val typing = fn(p, r, body = IfExpr(LocalVarExpr(p), LocalVarExpr(r), record("a" to IntExpr(1)))).typing(ctx)
        val t = typing.type.fn
        val param = t.paramTypes[1].tv
        val result = typing.bounds.lower(t.returnType.tv)
        assertEquals(RecordType(keys("a")).closed(), result.concrete)
        assertEquals(setOf(param), result.tvs)
    }

    @Test
    fun `two withs on one variable meet to the shared keys`() {
        val r = lv("r")
        val p = lv("p", 1)
        // The result carries r's keys and either a or b, so only r's keys for sure: it is r.
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "a" to IntExpr(1)), with(LocalVarExpr(r), "b" to IntExpr(2)))
        assertEquals("[a] Fn([Bool, {& a}] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `with result applied to a literal resolves to the union of keys`() {
        val r = lv("r")
        val call = CallExpr(fn(r, body = with(LocalVarExpr(r), "b" to IntExpr(1))), listOf(record("a" to IntExpr(1))))
        assertEquals(RecordType(keys("a", "b")).closed(), call.positiveType())
    }

    @Test
    fun `with on nil is an error`() {
        assertThrows<TypeCheckException> { with(NilExpr(), "a" to IntExpr(1)).typing(ctx) }
    }

    @Test
    fun `an undeclared key is an error`() {
        assertThrows<TypeCheckException> { record("zzz" to IntExpr(1)).typing(ctx) }
    }

    // {.?k & a} carries .k only where a does, so a demand for .k passes to a.
    @Test
    fun `a key given perhaps is demanded of the variable`() {
        val a = TypeVar()
        val env = emptyMap<TypeVar, Bounds>().constrain(OnVarType(mapOf(key("a") to Slot(false)), a), RecordType(keys("a")), ctx)
        assertThrows<TypeCheckException> { env.constrain(RecType(null, emptyList(), emptyMap(), closed = true), a.type(), ctx) }
    }

    @Test
    fun `a key given certainly satisfies the demand itself`() {
        val a = TypeVar()
        val env = emptyMap<TypeVar, Bounds>().constrain(OnVarType(mapOf(key("a") to Slot(true)), a), RecordType(keys("a")), ctx)
        env.constrain(RecType(null, emptyList(), emptyMap(), closed = true), a.type(), ctx)
    }

    // Both variants declare .value at the enum's argument, so its one instance is theirs, and with replaces it.
    @Test
    fun `with gives one type to a key every variant declares at one instance`() {
        val value = key("value")
        val warn = key("warn")
        val res = EnumRef(ns, "Res".sym)
        val ok = TagRef(ns, "Ok".sym)
        val partial = TagRef(ns, "Partial".sym)
        val (p, q, own) = List(3) { TypeVar() }
        val ctx = TypeCtx(
            keyTypes = { mapOf(value to KeyType(listOf(own), own.type()), warn to KeyType(BoolType))[it] },
            tagsByValue = mapOf(
                ok to TagInfo(ok, res, listOf(p), Payload.Record(setOf(value), mapOf(value to listOf(p.type())))),
                partial to TagInfo(partial, res, listOf(q), Payload.Record(setOf(value, warn), mapOf(value to listOf(q.type())))),
            ),
            variantsByEnum = mapOf(res to listOf(ok, partial)),
        )
        val t = withKeys(RecType(res, listOf(IntType), emptyMap()), mapOf(value to Slot(true, listOf(StrType))), ctx)
        assertEquals(RecType(null, emptyList(), mapOf(value to Slot(true, listOf(StrType)), warn to Slot(false))), t)
    }
}
