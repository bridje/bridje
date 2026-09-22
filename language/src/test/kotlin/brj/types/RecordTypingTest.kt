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

    private val ctx = TypeCtx(keyTypes = { key -> keyTypes[key.name.name] })

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

    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.positive(it.bounds) }
    private fun ValueExpr.shown(): String = typing(ctx).generalise().simplify(ctx).toString()

    @Test
    fun `record literal is its key set`() {
        assertEquals(RecordType(keys("a", "b")), record("a" to IntExpr(1), "b" to IntExpr(2)).positiveType())
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
        assertEquals(NullableType(IntType), getOpt("b", record("a" to IntExpr(1))).positiveType())
    }

    @Test
    fun `getter on a non-record is an error`() {
        assertThrows<TypeCheckException> { get("a", IntExpr(1)).typing(ctx) }
    }

    @Test
    fun `join of two literals keeps the shared keys`() {
        val joined = IfExpr(BoolExpr(true), record("a" to IntExpr(1), "b" to IntExpr(2)), record("a" to IntExpr(1), "c" to IntExpr(3)))
        assertEquals(RecordType(keys("a")), joined.positiveType())
        assertEquals(IntType, get("a", joined).positiveType())
        assertThrows<TypeCheckException> { get("b", joined).typing(ctx) }
        assertEquals(NullableType(IntType), getOpt("b", joined).positiveType())
    }

    @Test
    fun `with on a literal adds the key`() {
        assertEquals(RecordType(keys("a", "b")), with(record("a" to IntExpr(1)), "b" to IntExpr(2)).positiveType())
    }

    @Test
    fun `with value must fit the key's declared type`() {
        val r = lv("r")
        assertThrows<TypeCheckException> { fn(r, body = with(LocalVarExpr(r), "a" to StringExpr("s"))).typing(ctx) }
    }

    @Test
    fun `with on a variable is a meet, and demands only that it be a record`() {
        val r = lv("r")
        val typing = fn(r, body = with(LocalVarExpr(r), "b" to IntExpr(1))).typing(ctx)
        val t = typing.type as FnType
        val param = t.paramTypes.single() as TypeVar
        assertEquals(Meet(param, Filter.keys(keys("b"))), t.returnType)
        assertEquals(RecordType(emptySet()), typing.bounds.upper(param).concrete)
    }

    @Test
    fun `reading the added key after with makes no demand on the input`() {
        val r = lv("r")
        val typing = fn(r, body = get("b", with(LocalVarExpr(r), "b" to IntExpr(1)))).typing(ctx)
        val param = (typing.type as FnType).paramTypes.single() as TypeVar
        assertEquals(RecordType(emptySet()), typing.bounds.upper(param).concrete)
        assertEquals(IntType, typing.type.let { (it as FnType).returnType.positive(typing.bounds) })
    }

    @Test
    fun `reading another key after with demands it of the input`() {
        val r = lv("r")
        val typing = fn(r, body = get("a", with(LocalVarExpr(r), "b" to IntExpr(1)))).typing(ctx)
        val param = (typing.type as FnType).paramTypes.single() as TypeVar
        assertEquals(RecordType(keys("a")), typing.bounds.upper(param).concrete)
    }

    @Test
    fun `if returning with or the input absorbs to the input`() {
        // (fn (p r) (if p (with r .dirty true) r)) : the two uses of r are one variable once simplified,
        // and (r ∧ {dirty}) ∨ r is r.
        val r = lv("r")
        val p = lv("p", 1)
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "dirty" to BoolExpr(true)), LocalVarExpr(r))
        assertEquals("[a, ^(a, {})] Fn([Bool, a] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `if returning the input or a literal keeps both as lower bounds`() {
        val r = lv("r")
        val p = lv("p", 1)
        val typing = fn(p, r, body = IfExpr(LocalVarExpr(p), LocalVarExpr(r), record("a" to IntExpr(1)))).typing(ctx)
        val t = typing.type as FnType
        val param = t.paramTypes[1] as TypeVar
        val result = typing.bounds.lower(t.returnType as TypeVar)
        assertEquals(RecordType(keys("a")), result.concrete)
        assertEquals(setOf(param), result.tvs)
    }

    @Test
    fun `two withs on one variable meet to the shared keys`() {
        val r = lv("r")
        val p = lv("p", 1)
        // The result carries r's keys and either a or b, so only r's keys for sure: it is r.
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(r), "a" to IntExpr(1)), with(LocalVarExpr(r), "b" to IntExpr(2)))
        assertEquals("[a, ^(a, {})] Fn([Bool, a] a)", fn(p, r, body = body).shown())
    }

    @Test
    fun `with result applied to a literal resolves to the union of keys`() {
        val r = lv("r")
        val call = CallExpr(fn(r, body = with(LocalVarExpr(r), "b" to IntExpr(1))), listOf(record("a" to IntExpr(1))))
        assertEquals(RecordType(keys("a", "b")), call.positiveType())
    }

    @Test
    fun `set yields the old value or nil and leaves the record's type alone`() {
        val r = lv("r")
        val typing = fn(r, body = RecordSetExpr(LocalVarExpr(r), key("a"), IntExpr(1))).typing(ctx)
        val t = typing.type as FnType
        assertEquals(NullableType(IntType), t.returnType.positive(typing.bounds))
        assertEquals(RecordType(emptySet()), typing.bounds.upper(t.paramTypes.single() as TypeVar).concrete)
    }

    @Test
    fun `with on nil is an error`() {
        assertThrows<TypeCheckException> { with(NilExpr(), "a" to IntExpr(1)).typing(ctx) }
    }

    @Test
    fun `an undeclared key takes any value`() {
        assertEquals(RecordType(keys("zzz")), record("zzz" to IntExpr(1)).positiveType())
    }
}
