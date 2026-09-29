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
    private val okCtor = BridjeTagConstructor("Ok", listOf(key("value")))
    private val errCtor = BridjeTagConstructor("Err", listOf(key("error")))
    private val justCtor = BridjeTagConstructor("Just", listOf(key("value")))
    private val nothingVal = BridjeTaggedSingleton("Nothing")
    private val followerCtor = BridjeTagConstructor("Follower", listOf(key("knownLeader")))
    private val candidateCtor = BridjeTagConstructor("Candidate", listOf(key("votesReceived")))
    private val leaderCtor = BridjeTagConstructor("Leader", listOf(key("nextIndex"), key("matchIndex")))
    private val pairCtor = BridjeTagConstructor("Pair", listOf(key("fst"), key("snd")))
    private val userCtor = BridjeTagConstructor("User", listOf(key("fn"), key("ln")))

    // enum: Result(a, e) with tag: Ok{.value(a)} and tag: Err{.error(e)}; enum: Maybe(a) with tag: Just{.value(a)}.
    private val okParams = listOf(TypeVar(), TypeVar())
    private val errParams = listOf(TypeVar(), TypeVar())
    private val justParams = listOf(TypeVar())

    private val tags = mapOf<Any, TagInfo>(
        okCtor to TagInfo(tagRef("Ok"), result, okParams, Payload.Record(keys("value"), mapOf(key("value") to listOf(okParams[0].type())))),
        errCtor to TagInfo(tagRef("Err"), result, errParams, Payload.Record(keys("error"), mapOf(key("error") to listOf(errParams[1].type())))),
        justCtor to TagInfo(tagRef("Just"), maybe, justParams, Payload.Record(keys("value"), mapOf(key("value") to listOf(justParams[0].type())))),
        nothingVal to TagInfo(tagRef("Nothing"), maybe, listOf(TypeVar()), Payload.None),
        followerCtor to TagInfo(tagRef("Follower"), serverRole, emptyList(), Payload.Record(keys("knownLeader"))),
        candidateCtor to TagInfo(tagRef("Candidate"), serverRole, emptyList(), Payload.Record(keys("votesReceived"))),
        leaderCtor to TagInfo(tagRef("Leader"), serverRole, emptyList(), Payload.Record(keys("nextIndex", "matchIndex"))),
        pairCtor to TagInfo(tagRef("Pair"), null, emptyList(), Payload.Record(keys("fst", "snd"))),
        userCtor to TagInfo(tagRef("User"), null, emptyList(), Payload.Record(keys("fn", "ln"))),
    )

    private fun oneParam() = TypeVar().let { KeyType(listOf(it), it.type()) }

    private val keyTypes = mapOf(
        "knownLeader" to KeyType(IntType.nullable()), "votesReceived" to KeyType(IntType),
        "nextIndex" to KeyType(IntType), "matchIndex" to KeyType(IntType),
        "fn" to KeyType(StrType), "ln" to KeyType(StrType), "email" to KeyType(StrType),
        "fst" to KeyType(IntType), "snd" to KeyType(StrType),
        "value" to oneParam(), "error" to oneParam(),
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
    // Binds the tag's keys, in the order the tag declares them, to the given locals.
    private fun tagPat(ctor: Any, vararg bindings: LocalVar) = TagPattern(
        ctor,
        if (bindings.isEmpty()) null
        else PayloadBinding.Keys((ctor as TagConstructor).keys.zip(bindings).map { (k, lv) -> BridjeKey(k.ns, k.name) to lv }),
    )
    private fun wholePat(ctor: Any, binding: LocalVar) = TagPattern(ctor, PayloadBinding.Whole(binding))
    private fun ok(value: ValueExpr) = ctor(okCtor, record("value" to value))
    private fun err(error: ValueExpr) = ctor(errCtor, record("error" to error))
    private fun just(value: ValueExpr) = ctor(justCtor, record("value" to value))

    private fun ValueExpr.positiveType(): Type = typing(ctx).let { it.type.shown(it.bounds, ctx) }

    private val follower = ctor(followerCtor, record("knownLeader" to IntExpr(1)))
    private val candidate = ctor(candidateCtor, record("votesReceived" to IntExpr(2)))
    private val user = ctor(userCtor, record("fn" to StringExpr("a"), "ln" to StringExpr("b")))

    @Test
    fun `constructor application is the tag with its enum's arguments`() {
        val t = ok(IntExpr(1)).positiveType().base as Base.Rec
        assertEquals(tagRef("Ok"), t.name)
        assertEquals(IntType, t.args[0])
        assertTrue(t.args[1].base is TypeVar)
    }

    // Neither key is one every variant declares, so the join keeps each as the variant that carries it has it.
    @Test
    fun `two tags of one enum join to the enum`() {
        val t = IfExpr(BoolExpr(true), ok(IntExpr(1)), err(StringExpr("s"))).positiveType()
        val keys = mapOf(key("value") to Slot(false, listOf(IntType)), key("error") to Slot(false, listOf(StrType)))
        assertEquals(RecType(result, listOf(IntType, StrType), keys, closed = true), t)
    }

    @Test
    fun `a nullary variant joins with a unary one`() {
        val t = IfExpr(BoolExpr(true), just(IntExpr(1)), gv("Nothing", nothingVal)).positiveType()
        assertEquals(EnumType(maybe, listOf(IntType)).closed(), t)
    }

    @Test
    fun `the same tag with conflicting payloads is an error`() {
        assertThrows<TypeCheckException> {
            IfExpr(BoolExpr(true), ok(IntExpr(1)), ok(StringExpr("s"))).typing(ctx)
        }
    }

    @Test
    fun `tags of different enums join to the keys they share`() {
        val typing = IfExpr(BoolExpr(true), ok(IntExpr(1)), just(IntExpr(2))).typing(ctx)
        assertEquals("{t/.value(Int)}", typing.type.shown(typing.bounds, ctx).toString())
    }

    @Test
    fun `a tag with no record joins with no other tag`() {
        assertThrows<TypeCheckException> {
            IfExpr(BoolExpr(true), gv("Nothing", nothingVal), follower).typing(ctx)
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
        val t = typing.type.fn
        val demand = typing.bounds.upper(t.paramTypes.single().tv).concrete!!.base as Base.Rec
        assertEquals(result, demand.name)
        val res = typing.bounds.lower(t.returnType.tv)
        assertEquals(IntType, res.concrete)
        assertTrue(demand.args[0].tv in res.tvs)
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
        val param = typing.type.fn.paramTypes.single().tv
        assertEquals(result, (typing.bounds.upper(param).concrete!!.base as Base.Rec).name)
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
        val res = typing.bounds.lower(typing.type.fn.returnType.tv)
        assertEquals(tagRef("Nothing"), (res.concrete.base as Base.Rec).name)
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
    fun `a bare pattern ignores the payload`() {
        val x = lv("x")
        fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(tagPat(okCtor), IntExpr(0)),
            branch(tagPat(errCtor), IntExpr(0)),
        ))).typing(ctx)
    }

    @Test
    fun `constructing a tag demands its keys`() {
        assertThrows<TypeCheckException> { ctor(userCtor, record("fn" to StringExpr("a"))).typing(ctx) }
    }

    @Test
    fun `a record-payload tag reads as its payload`() {
        assertEquals(IntType.nullable(), get("knownLeader", follower).positiveType())
    }

    @Test
    fun `an enum reads only what every variant carries`() {
        val role = IfExpr(BoolExpr(true), follower, candidate)
        assertEquals(EnumType(serverRole, emptyList()).closed(), role.positiveType())
        assertThrows<TypeCheckException> { get("knownLeader", role).typing(ctx) }
        assertEquals(IntType.nullable(), getOpt("knownLeader", role).positiveType())
    }

    @Test
    fun `a record-payload pattern binds the payload record`() {
        val x = lv("x")
        val f = lv("f", 1)
        val other = lv("other", 2)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(
            branch(wholePat(followerCtor, f), get("knownLeader", LocalVarExpr(f))),
            branch(CatchAllBindingPattern(other), NilExpr()),
        ))).typing(ctx)
        val t = typing.type.fn
        assertEquals(IntType.nullable(), t.returnType.shown(typing.bounds, ctx))
    }

    @Test
    fun `a declared tag's pattern binds the tagged value itself`() {
        val x = lv("x")
        val p = lv("p", 1)
        val typing = fn(x, body = CaseExpr(LocalVarExpr(x), listOf(branch(wholePat(pairCtor, p), LocalVarExpr(p))))).typing(ctx)
        assertEquals(TagType(tagRef("Pair"), emptyList()), typing.type.fn.returnType.shown(typing.bounds, ctx))
    }

    @Test
    fun `with on a nominal is the nominal, known to carry the key as well`() {
        val withEmail = with(user, "email" to StringExpr("x"))
        val t = withEmail.positiveType().base as Base.Rec
        assertEquals(tagRef("User"), t.name)
        assertEquals(keys("email"), t.keys.keys)
        assertEquals(StrType, get("email", withEmail).positiveType())
        assertEquals(StrType, get("fn", withEmail).positiveType())
    }

    @Test
    fun `a tag constructed with keys beyond its own is known to carry them`() {
        val u = ctor(userCtor, record("fn" to StringExpr("a"), "ln" to StringExpr("b"), "email" to StringExpr("c")))
        assertEquals(TagType(tagRef("User"), emptyList(), keys("email")).closed(), u.positiveType())
        assertEquals(StrType, get("email", u).positiveType())
        assertThrows<TypeCheckException> { get("email", user).typing(ctx) }
    }

    @Test
    fun `a join of one tag keeps the keys both carry, and those one carries as optional`() {
        val withEmail = with(user, "email" to StringExpr("x"))
        assertEquals("User{t/.?email}", IfExpr(BoolExpr(true), withEmail, user).positiveType().toString())
        assertEquals(TagType(tagRef("User"), emptyList(), keys("email")).closed(), IfExpr(BoolExpr(true), withEmail, withEmail).positiveType())
    }

    @Test
    fun `a tag demanded to carry a key it may not carry is the tag known to carry it`() {
        val a = freshVar()
        val met = emptyMap<TypeVar, Bounds>().constrain(a, TagType(tagRef("User"), emptyList()), ctx).constrain(a, RecordType(keys("email")), ctx)
        assertEquals(TagType(tagRef("User"), emptyList(), keys("email")), met.upper(a.tv).concrete)
        assertThrows<TypeCheckException> { met.constrain(TagType(tagRef("User"), emptyList()), a, ctx) }
        met.constrain(TagType(tagRef("User"), emptyList(), keys("email")), a, ctx)
    }

    @Test
    fun `with on a nominal's own key is the nominal`() {
        val t = with(user, "fn" to StringExpr("x")).positiveType()
        assertEquals(tagRef("User"), (t.base as Base.Rec).name)
    }

    @Test
    fun `ifLet narrows its binding past nil`() {
        val x = lv("x")
        val d = lv("d", 1)
        val y = lv("y", 2)
        val orElse = fn(x, d, body = IfLetExpr(y, LocalVarExpr(x), LocalVarExpr(y), LocalVarExpr(d)))
        val call = CallExpr(orElse, listOf(IfExpr(BoolExpr(true), IntExpr(1), NilExpr()), IntExpr(0)))
        assertEquals(IntType, call.positiveType())
    }

    @Test
    fun `a standalone tag over keys of one type each has no arguments`() {
        val t = ctor(pairCtor, record("fst" to IntExpr(1), "snd" to StringExpr("s"))).positiveType().base as Base.Rec
        assertEquals(tagRef("Pair"), t.name)
        assertTrue(t.args.isEmpty())
    }
}
