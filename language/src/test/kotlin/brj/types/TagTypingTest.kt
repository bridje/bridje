package brj.types

import brj.GlobalVar
import brj.analyser.*
import brj.runtime.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TagTypingTest {

    private val ns = "t".sym

    private fun key(name: String) = QSymbol(ns, name.sym)
    private fun keys(vararg names: String) = names.map { key(it) }.toSet()
    private fun tagRef(name: String) = TagRef(ns, name.sym)
    private fun enumRef(name: String) = EnumRef(ns, name.sym)

    private val result = enumRef("Result")
    private val maybe = enumRef("Maybe")
    private val serverRole = enumRef("ServerRole")

    // Runtime values as the analyser puts them into the Expr tree.
    private val okCtor = BridjeTagConstructor("Ok", 1, listOf(key("a")))
    private val errCtor = BridjeTagConstructor("Err", 1, listOf(key("e")))
    private val justCtor = BridjeTagConstructor("Just", 1, listOf(key("a")))
    private val nothingVal = BridjeTaggedSingleton("Nothing")
    private val followerCtor = BridjeTagConstructor("Follower", 1, listOf(key("knownLeader")))
    private val candidateCtor = BridjeTagConstructor("Candidate", 1, listOf(key("votesReceived")))
    private val leaderCtor = BridjeTagConstructor("Leader", 2, listOf(key("nextIndex"), key("matchIndex")))
    private val pairCtor = BridjeTagConstructor("Pair", 2, listOf(key("first"), key("second")))
    private val userCtor = BridjeTagConstructor("User", 2, listOf(key("fn"), key("ln")))

    private val tags = mapOf<Any, TagInfo>(
        okCtor to TagInfo(tagRef("Ok"), result, 2, Payload.Positional(listOf(FieldType.Param(0)))),
        errCtor to TagInfo(tagRef("Err"), result, 2, Payload.Positional(listOf(FieldType.Param(1)))),
        justCtor to TagInfo(tagRef("Just"), maybe, 1, Payload.Positional(listOf(FieldType.Param(0)))),
        nothingVal to TagInfo(tagRef("Nothing"), maybe, 1, Payload.None),
        followerCtor to TagInfo(tagRef("Follower"), serverRole, 0, Payload.Record(keys("knownLeader"))),
        candidateCtor to TagInfo(tagRef("Candidate"), serverRole, 0, Payload.Record(keys("votesReceived"))),
        leaderCtor to TagInfo(tagRef("Leader"), serverRole, 0, Payload.Record(keys("nextIndex", "matchIndex"))),
        pairCtor to TagInfo(tagRef("Pair"), null, 0, Payload.Positional(listOf(FieldType.Untracked, FieldType.Untracked))),
        userCtor to TagInfo(tagRef("User"), null, 0, Payload.Record(keys("fn", "ln"))),
    )

    private val keyTypes = mapOf(
        "knownLeader" to NullableType(IntType), "votesReceived" to IntType,
        "nextIndex" to IntType, "matchIndex" to IntType,
        "fn" to StrType, "ln" to StrType, "email" to StrType,
    )

    private val ctx = TypeCtx(
        keyTypes = { keyTypes[it.name.name] },
        tagsByValue = tags,
        variantsByEnum = mapOf(
            result to listOf(tagRef("Ok"), tagRef("Err")),
            maybe to listOf(tagRef("Just"), tagRef("Nothing")),
            serverRole to listOf(tagRef("Follower"), tagRef("Candidate"), tagRef("Leader")),
        ),
    )

    private fun gv(name: String, value: Any) = GlobalVarExpr(GlobalVar(ns, name.sym, value))
    private fun ctor(ctor: BridjeTagConstructor, vararg args: ValueExpr) = CallExpr(gv(ctor.tag, ctor), args.toList())
    private fun record(vararg fields: Pair<String, ValueExpr>) = RecordExpr(fields.map { (k, v) -> key(k) to v })
    private fun getter(name: String) = GlobalVarExpr(GlobalVar(ns, name.sym, BridjeKey(ns, name.sym)))
    private fun optGetter(name: String) = GlobalVarExpr(GlobalVar(ns, "?$name".sym, BridjeOptionalKey(BridjeKey(ns, name.sym))))
    private fun get(name: String, target: ValueExpr) = CallExpr(getter(name), listOf(target))
    private fun getOpt(name: String, target: ValueExpr) = CallExpr(optGetter(name), listOf(target))
    private fun with(r: ValueExpr, vararg fields: Pair<String, ValueExpr>) = RecordUpdateExpr(r, fields.map { (k, v) -> key(k) to v })
    private fun lv(name: String, slot: Int = 0) = LocalVar(name.sym, slot)
    private fun fn(vararg params: LocalVar, body: ValueExpr) = FnExpr("f".sym, params.toList(), body, params.size, emptyList(), false)
    private fun branch(pattern: CasePattern, body: ValueExpr) = CaseBranch(pattern, body)
    private fun tagPat(ctor: Any, vararg bindings: LocalVar) = TagPattern(ctor, bindings.toList())

    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.positive(it.bounds, ctx) }

    private val follower = ctor(followerCtor, record("knownLeader" to IntExpr(1)))
    private val candidate = ctor(candidateCtor, record("votesReceived" to IntExpr(2)))
    private val user = ctor(userCtor, record("fn" to StringExpr("a"), "ln" to StringExpr("b")))

    @Test
    fun `constructor application is the tag with its enum's arguments`() {
        val t = ctor(okCtor, IntExpr(1)).positiveType() as TagType
        assertEquals(tagRef("Ok"), t.tag)
        assertEquals(IntType, t.args[0])
        assertTrue(t.args[1] is TypeVar)
    }

    @Test
    fun `two tags of one enum join to the enum`() {
        val t = IfExpr(BoolExpr(true), ctor(okCtor, IntExpr(1)), ctor(errCtor, StringExpr("s"))).positiveType()
        assertEquals(EnumType(result, listOf(IntType, StrType)), t)
    }

    @Test
    fun `a nullary variant joins with a unary one`() {
        val t = IfExpr(BoolExpr(true), ctor(justCtor, IntExpr(1)), gv("Nothing", nothingVal)).positiveType()
        assertEquals(EnumType(maybe, listOf(IntType)), t)
    }

    @Test
    fun `the same tag with conflicting payloads is an error`() {
        assertThrows<TypeCheckException> {
            IfExpr(BoolExpr(true), ctor(okCtor, IntExpr(1)), ctor(okCtor, StringExpr("s"))).typing(ctx)
        }
    }

    @Test
    fun `tags of different enums do not join`() {
        assertThrows<TypeCheckException> {
            IfExpr(BoolExpr(true), ctor(okCtor, IntExpr(1)), follower).typing(ctx)
        }
    }

    @Test
    fun `exhaustive case demands the enum and binds payloads by its arguments`() {
        val x = lv("x")
        val v = lv("v", 1)
        val e = lv("e", 2)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(okCtor, v), LocalVarExpr(v)),
            branch(tagPat(errCtor, e), IntExpr(0)),
        ))).typing(ctx)
        val t = typing.type as FnType
        val demand = typing.bounds.upper(t.paramTypes.single() as TypeVar).concrete as EnumType
        assertEquals(result, demand.enum)
        val res = typing.bounds.lower(t.returnType as TypeVar)
        assertEquals(IntType, res.concrete)
        assertTrue(demand.args[0] in res.tvs)
    }

    @Test
    fun `non-exhaustive case without a default is an error`() {
        val x = lv("x")
        val v = lv("v", 1)
        assertThrows<TypeCheckException> {
            fn(x, body = CaseExpr(LocalVarExpr(x), listOf(branch(tagPat(okCtor, v), LocalVarExpr(v))))).typing(ctx)
        }
    }

    @Test
    fun `a default makes a partial case fine and still demands the enum`() {
        val x = lv("x")
        val v = lv("v", 1)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(okCtor, v), LocalVarExpr(v)),
            branch(DefaultPattern(), IntExpr(0)),
        ))).typing(ctx)
        val param = (typing.type as FnType).paramTypes.single() as TypeVar
        assertEquals(result, (typing.bounds.upper(param).concrete as EnumType).enum)
    }

    @Test
    fun `a catch-all binding narrows to the one remaining variant`() {
        // (fn (x) (case x (Just v) v other other)) — other is Nothing
        val x = lv("x")
        val v = lv("v", 1)
        val other = lv("other", 2)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(justCtor, v), LocalVarExpr(v)),
            branch(CatchAllBindingPattern(other), LocalVarExpr(other)),
        ))).typing(ctx)
        val res = typing.bounds.lower((typing.type as FnType).returnType as TypeVar)
        assertEquals(tagRef("Nothing"), (res.concrete as TagType).tag)
    }

    @Test
    fun `patterns from different enums are an error`() {
        val x = lv("x")
        val v = lv("v", 1)
        val f = lv("f", 2)
        assertThrows<TypeCheckException> {
            fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
                branch(tagPat(okCtor, v), LocalVarExpr(v)),
                branch(tagPat(followerCtor, f), IntExpr(0)),
            ))).typing(ctx)
        }
    }

    @Test
    fun `a pattern binds all of the payload or none of it`() {
        val x = lv("x")
        fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(okCtor), IntExpr(0)),
            branch(tagPat(errCtor), IntExpr(0)),
        ))).typing(ctx)
        assertThrows<TypeCheckException> {
            fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
                branch(tagPat(okCtor, lv("a", 1), lv("b", 2)), IntExpr(0)),
                branch(tagPat(errCtor), IntExpr(0)),
            ))).typing(ctx)
        }
    }

    @Test
    fun `a record-payload tag reads as its payload`() {
        assertEquals(NullableType(IntType), get("knownLeader", follower).positiveType())
    }

    @Test
    fun `an enum reads only what every variant carries`() {
        val role = IfExpr(BoolExpr(true), follower, candidate)
        assertEquals(EnumType(serverRole, emptyList()), role.positiveType())
        assertThrows<TypeCheckException> { get("knownLeader", role).typing(ctx) }
        assertEquals(NullableType(IntType), getOpt("knownLeader", role).positiveType())
    }

    @Test
    fun `a record-payload pattern binds the payload record`() {
        val x = lv("x")
        val f = lv("f", 1)
        val other = lv("other", 2)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(followerCtor, f), get("knownLeader", LocalVarExpr(f))),
            branch(CatchAllBindingPattern(other), NilExpr()),
        ))).typing(ctx)
        val t = typing.type as FnType
        assertEquals(NullableType(IntType), t.returnType.positive(typing.bounds, ctx))
    }

    @Test
    fun `with on a nominal promotes it`() {
        val promoted = with(user, "email" to StringExpr("x"))
        val t = promoted.positiveType() as Meet
        assertEquals(tagRef("User"), (t.base as TagType).tag)
        assertEquals(Filter.keys(keys("email")), t.filter)
        assertEquals(StrType, get("email", promoted).positiveType())
        assertEquals(StrType, get("fn", promoted).positiveType())
    }

    @Test
    fun `with on a nominal's own key is the nominal`() {
        val t = with(user, "fn" to StringExpr("x")).positiveType()
        assertEquals(tagRef("User"), (t as TagType).tag)
    }

    @Test
    fun `a promotion joined with the plain nominal demotes`() {
        val u = lv("u")
        val p = lv("p", 1)
        val body = IfExpr(LocalVarExpr(p), with(LocalVarExpr(u), "email" to StringExpr("x")), LocalVarExpr(u))
        val t = LetExpr(u, user, fn(p, body = body)).positiveType() as FnType
        assertEquals(tagRef("User"), (t.returnType as TagType).tag)
    }

    @Test
    fun `nil-checking a variable narrows the catch-all binding`() {
        val x = lv("x")
        val d = lv("d", 1)
        val y = lv("y", 2)
        val orElse = fn(x, d, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(NilPattern(), LocalVarExpr(d)),
            branch(CatchAllBindingPattern(y), LocalVarExpr(y)),
        )))
        val call = CallExpr(orElse, listOf(IfExpr(BoolExpr(true), IntExpr(1), NilExpr()), IntExpr(0)))
        assertEquals(IntType, call.positiveType())
    }

    @Test
    fun `a nil-only case without a default needs a nil scrutinee`() {
        CaseExpr(NilExpr(), listOf(branch(NilPattern(), IntExpr(42)))).typing(ctx)
        assertThrows<TypeCheckException> {
            CaseExpr(IntExpr(10), listOf(branch(NilPattern(), IntExpr(0)))).typing(ctx)
        }
    }

    @Test
    fun `a standalone positional tag has untracked fields`() {
        val t = ctor(pairCtor, IntExpr(1), StringExpr("s")).positiveType() as TagType
        assertEquals(tagRef("Pair"), t.tag)
        assertTrue(t.args.isEmpty())
    }
}
