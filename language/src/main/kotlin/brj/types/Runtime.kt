package brj.types

import brj.GlobalVar
import brj.NsEnv
import brj.analyser.FnExpr
import brj.analyser.ValueExpr
import brj.runtime.*

// The builtin metas are constructors the runtime defines in Kotlin. The reader's form metas are the
// variants of Form and carry what the reader puts in them; the others' payloads are untracked.
private val symbolType = TagType(TagRef(Symbol.intern("brj.core"), Symbol.intern("Symbol")), emptyList())
private val formsType = VectorType(FormType)
private val formPayloads: Map<String, List<Type>> = mapOf(
    "SymbolForm" to listOf(symbolType), "DotSymbolForm" to listOf(symbolType),
    "QSymbolForm" to listOf(symbolType, symbolType), "QDotSymbolForm" to listOf(symbolType, symbolType),
    "List" to listOf(formsType), "Vector" to listOf(formsType), "Set" to listOf(formsType), "Record" to listOf(formsType),
    "Int" to listOf(IntType), "Double" to listOf(DoubleType), "String" to listOf(StrType),
    "BigInt" to listOf(BigIntType), "BigDec" to listOf(BigDecType),
)

private fun untracked(n: Int) = Payload.Positional(List(n) { FieldType.Untracked })

// The checker's view of what the runtime has loaded: declared key types, the tags each namespace
// declares by their runtime constructors, enum membership, and the builtin metas. `current` is the
// namespace being evaluated, whose definitions so far are not yet registered with the context.
fun typeCtx(
    ctx: BridjeContext,
    current: NsEnv? = null,
    schemes: (GlobalVar) -> Scheme? = { null },
): TypeCtx {
    val namespaces = ctx.namespaces + (current?.let { mapOf(it.nsSymbol to it) } ?: emptyMap())
    val tags = HashMap<Any, TagInfo>()
    val variants = HashMap<EnumRef, List<TagRef>>()

    for ((nsName, ns) in namespaces) {
        for ((sym, info) in ns.tags) ns.vars[sym]?.value?.let { tags[it] = info }
        for ((_, gv) in ns.vars) {
            val v = gv.value
            // A builtin meta may be visible from several namespaces; it is one tag.
            if (v is BuiltinMetaObj) tags.putIfAbsent(v, TagInfo(
                TagRef(v.ns, v.tagName),
                if (v.ns == FormEnum.ns) FormEnum else null,
                0,
                formPayloads[v.tagName.name]?.let { Payload.Positional(it.map(FieldType::Fixed)) } ?: untracked(1),
            ))
        }
        for ((enumName, members) in ns.enums) {
            variants[EnumRef(nsName, enumName)] = members.map { TagRef(nsName, it) }
        }
    }
    variants[FormEnum] = tags.values.filter { it.enum == FormEnum }.map { it.tag }

    return TypeCtx(
        keyTypes = { key -> namespaces[key.ns]?.keyTypes?.get(key.name) },
        tagsByValue = tags,
        variantsByEnum = variants,
        globalSchemes = schemes,
    )
}

// A macro takes forms (a vector of them for its rest parameter) and yields a form.
fun macroType(fn: FnExpr): FnType = FnType(
    fn.params.indices.map { i -> if (fn.isVariadic && i == fn.params.lastIndex) VectorType(FormType) else FormType },
    FormType,
)

// Type checking at the top level: a definition's scheme, checked against its declaration where there
// is one, and a macro checked as a function over forms.
object Types {
    fun infer(ctx: BridjeContext, nsEnv: NsEnv, value: ValueExpr, declared: (TypeCtx) -> Type? = { null }): Scheme {
        val typeCtx = typeCtx(ctx, nsEnv)
        val inferred = value.typing(typeCtx).generalise()
        return declared(typeCtx)?.let { checkDeclared(inferred, it, typeCtx) } ?: inferred
    }

    fun inferDef(ctx: BridjeContext, nsEnv: NsEnv, value: ValueExpr, declared: Type?): Scheme =
        infer(ctx, nsEnv, value) { declared }

    fun inferMacro(ctx: BridjeContext, nsEnv: NsEnv, fn: FnExpr): Scheme = infer(ctx, nsEnv, fn) { macroType(fn) }

    fun check(ctx: BridjeContext, nsEnv: NsEnv, value: ValueExpr) {
        value.typing(typeCtx(ctx, nsEnv))
    }
}
