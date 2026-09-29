package brj.types

import brj.GlobalVar
import brj.runtime.QSymbol

// A key's declared type, `decl: [a] .value a`: the type, over the key's parameters, which a record's slot
// instantiates. A key declared without type variables has none, and one type everywhere.
data class KeyType(val params: List<TypeVar>, val type: Type) {
    constructor(type: Type) : this(emptyList(), type)

    init {
        require(type.typeVars().all { it in params }) { "key type $type mentions variables beyond its parameters $params" }
    }

    fun at(args: List<Type>): Type {
        require(args.size == params.size) { "key type $this at $args" }
        return type.substitute(params.zip(args).toMap())
    }
}

sealed interface Payload {
    data object None : Payload

    // A key the tag gives arguments to, `.value(a)`, is instantiated at them, in terms of the tag's
    // parameters; any other is declared without type variables.
    data class Record(val keys: Set<QSymbol>, val keyArgs: Map<QSymbol, List<Type>> = emptyMap()) : Payload {
        fun template(key: QSymbol): List<Type> = keyArgs[key].orEmpty()
    }

    // A builtin meta object called with arguments the checker does not track: Symbol, Var and File.
    data class Opaque(val arity: Int) : Payload
}

// What the checker knows about a declared tag. A tag inside an enum takes the enum's type parameters, in order.
data class TagInfo(val tag: TagRef, val enum: EnumRef?, val params: List<TypeVar>, val payload: Payload) {
    val paramCount get() = params.size

    init {
        (payload as? Payload.Record)?.keyArgs?.values?.flatten()?.forEach { t ->
            require(t.typeVars().all { it in params }) { "$tag gives a key $t, beyond its parameters $params" }
        }
    }
}

// What a record's type says of one key across the forms the record may take: certainly present or not, and
// the instances its value may be at. A tag's is one; an enum's is one per variant that declares the key,
// and variants that agree give one.
data class Facet(val required: Boolean, val instances: Set<List<Type>>)

// How a type argument's type varies with it: with it, against it, both, or not at all.
enum class Variance { CO, CONTRA, INV, PHANTOM }

private fun variance(occurrences: Set<Boolean>) = when {
    occurrences.size == 2 -> Variance.INV
    true in occurrences -> Variance.CO
    false in occurrences -> Variance.CONTRA
    else -> Variance.PHANTOM
}

// What the checker needs from the world outside the expression: declared key types, tag declarations
// and enum membership. Until the analyser records these itself they are supplied by the caller.
class TypeCtx(
    private val keyTypes: (QSymbol) -> KeyType? = { null },
    // Keyed by the runtime constructor or singleton object, which is what the analyser puts in the Expr.
    private val tagsByValue: Map<Any, TagInfo> = emptyMap(),
    private val variantsByEnum: Map<EnumRef, List<TagRef>> = emptyMap(),
    // Schemes of globals, over and above what the globals themselves carry.
    private val globalSchemes: (GlobalVar) -> Scheme? = { null },
) {
    fun globalScheme(gv: GlobalVar): Scheme? = globalSchemes(gv) ?: gv.scheme

    private val tagsByRef = tagsByValue.values.associateBy { it.tag }

    fun keyType(key: QSymbol): KeyType =
        keyTypes(key) ?: throw TypeCheckException("${key.toDisplayString()} has no declared type")

    fun isMono(key: QSymbol): Boolean = keyType(key).params.isEmpty()

    // Fresh arguments for the key's parameters: the instance a new value of it is at.
    fun freshInstance(key: QSymbol): List<Type> = keyType(key).params.map { freshVar() }

    fun keyTypeAt(key: QSymbol, args: List<Type>): Type {
        val kt = keyType(key)
        if (args.size != kt.params.size) throw TypeCheckException("${key.toDisplayString()} takes ${kt.params.size} type arguments, not ${args.size}")
        return kt.at(args)
    }

    fun tagInfo(value: Any): TagInfo =
        tagsByValue[value] ?: throw TypeCheckException("unknown tag $value")

    fun tagInfo(ref: TagRef): TagInfo =
        tagsByRef[ref] ?: throw TypeCheckException("unknown tag $ref")

    fun variants(enum: EnumRef): List<TagInfo> =
        (variantsByEnum[enum] ?: throw TypeCheckException("unknown enum $enum")).map { tagInfo(it) }

    fun paramCount(name: Name): Int = when (name) {
        is TagRef -> tagInfo(name).paramCount
        is EnumRef -> variants(name).firstOrNull()?.paramCount ?: 0
    }

    private fun Payload.record() = this as? Payload.Record

    // A tag's name implies its record, if it has one; an enum's, if every variant has one. No name is a record.
    fun isRecord(name: Name?): Boolean = when (name) {
        null -> true
        is TagRef -> tagInfo(name).payload is Payload.Record
        is EnumRef -> variants(name).all { it.payload is Payload.Record }
    }

    // Whether a value of the name may carry keys at all: a tag or an enum with no record carries none, so
    // whether its type is open says nothing.
    fun mayCarryKeys(name: Name?): Boolean = when (name) {
        null -> true
        is TagRef -> tagInfo(name).payload is Payload.Record
        is EnumRef -> variants(name).any { it.payload is Payload.Record }
    }

    // An enum's one variant with a record, or null where it has none or several.
    fun onlyRecordVariant(name: Name): TagRef? = when (name) {
        is TagRef -> null
        is EnumRef -> variants(name).filter { it.payload is Payload.Record }.singleOrNull()?.tag
    }

    // The name's own keys at [args]: a tag's are present. An enum's are present where every variant declares
    // them, and perhaps present where the others have no record, so cannot carry them. A variant with a record
    // that does not declare a key may carry it anyway, at any type, so an enum declares a key with type
    // variables only where no such variant does; a key without them has its one type wherever it is.
    fun declared(name: Name, args: List<Type>): Map<QSymbol, Facet> {
        fun of(info: TagInfo): Map<QSymbol, List<Type>>? {
            val record = info.payload.record() ?: return null
            val at = info.params.zip(args).toMap()
            return record.keys.associateWith { k -> record.template(k).map { it.substitute(at) } }
        }
        return when (name) {
            is TagRef -> of(tagInfo(name)).orEmpty().mapValues { (_, inst) -> Facet(true, setOf(inst)) }
            is EnumRef -> {
                val variants = variants(name).map(::of)
                val records = variants.filterNotNull()
                records.flatMap { it.keys }.distinct()
                    .filter { k -> isMono(k) || records.all { k in it } }
                    .associateWith { k -> Facet(variants.all { it != null && k in it }, records.mapNotNull { it[k] }.toSet()) }
            }
        }
    }

    // Everything the record's type says of each key it mentions: its name's, and its own beyond them.
    fun slotsOf(rec: Base.Rec): Map<QSymbol, Facet> {
        val out = LinkedHashMap(rec.name?.let { declared(it, rec.args) }.orEmpty())
        for ((k, s) in rec.keys) {
            val had = out[k]
            out[k] = Facet(s.required || had?.required == true, had?.instances.orEmpty() + setOf(s.args))
        }
        return out
    }

    // l's name is at least as specific as u's: the tag itself, its enum, or no name at all.
    fun nameBelow(l: Name?, u: Name?): Boolean = when (u) {
        null -> isRecord(l)
        is TagRef -> l == u
        is EnumRef -> l == u || (l is TagRef && tagInfo(l).enum == u)
    }

    // The least name both are below, or null for none: the tag, the enum of two of its tags, or no name.
    fun commonName(a: Name?, b: Name?): Name? = when {
        a == b -> a
        a is TagRef && b is TagRef -> tagInfo(a).enum?.takeIf { it == tagInfo(b).enum }
        a is TagRef && b is EnumRef -> b.takeIf { tagInfo(a).enum == b }
        a is EnumRef && b is TagRef -> a.takeIf { tagInfo(b).enum == a }
        else -> null
    }

    // The most specific of the two, or null where nothing is below both: two tags, or a tag outside the enum.
    fun specificName(a: Name?, b: Name?): Name? = when {
        a == null -> b
        b == null -> a
        a == b -> a
        a is TagRef && b is EnumRef -> a.takeIf { tagInfo(a).enum == b }
        a is EnumRef && b is TagRef -> b.takeIf { tagInfo(b).enum == a }
        else -> null
    }

    // Variance is where a parameter occurs in the types it reaches: a tag's through the keys its arguments
    // instantiate, an enum's through its variants', a key's in its declared type. A name or a key may reach
    // itself, as a recursive enum does, so the occurrences grow to a fixpoint from none.
    private val nameOcc = HashMap<Name, List<Set<Boolean>>>()
    private val keyOcc = HashMap<QSymbol, List<Set<Boolean>>>()
    private var grew = false

    private fun nameOccurrences(name: Name): List<Set<Boolean>> =
        nameOcc.getOrPut(name) { grew = true; List(paramCount(name)) { emptySet() } }

    private fun keyOccurrences(key: QSymbol): List<Set<Boolean>> =
        keyOcc.getOrPut(key) { grew = true; List(keyType(key).params.size) { emptySet() } }

    // Where [tv] occurs in [t], taken at polarity [pos].
    private fun occurrences(tv: TypeVar, t: Type, pos: Boolean, out: MutableSet<Boolean>) {
        fun via(vs: List<Set<Boolean>>, args: List<Type>) = args.forEachIndexed { i, a ->
            vs.getOrNull(i)?.forEach { q -> occurrences(tv, a, if (q) pos else !pos, out) }
        }
        fun viaKeys(keys: Map<QSymbol, Slot>) = keys.forEach { (k, s) -> if (s.args.isNotEmpty()) via(keyOccurrences(k), s.args) }
        when (val b = t.base) {
            is TypeVar -> if (b == tv) out += pos
            is Base.Fn -> { b.paramTypes.forEach { occurrences(tv, it, !pos, out) }; occurrences(tv, b.returnType, pos, out) }
            is Base.Host -> b.args.forEach { occurrences(tv, it, pos, out); occurrences(tv, it, !pos, out) }
            is Base.Rec -> { b.name?.let { via(nameOccurrences(it), b.args) }; viaKeys(b.keys) }
            is Base.OnVar -> { if (b.base == tv) out += pos; viaKeys(b.keys) }
            is Base.Vector -> occurrences(tv, b.el, pos, out)
            is Base.Set -> occurrences(tv, b.el, pos, out)
            is Base.Iterable -> occurrences(tv, b.el, pos, out)
            is Base.Iterator -> occurrences(tv, b.el, pos, out)
            is Base.Prim, Base.Nothing -> {}
        }
    }

    private fun computeName(name: Name): List<Set<Boolean>> = when (name) {
        is TagRef -> {
            val info = tagInfo(name)
            val record = info.payload.record()
            info.params.map { p ->
                buildSet {
                    for (k in record?.keys.orEmpty()) {
                        val template = record!!.template(k)
                        if (template.isNotEmpty()) template.zip(keyOccurrences(k)).forEach { (arg, qs) ->
                            qs.forEach { q -> occurrences(p, arg, q, this) }
                        }
                    }
                }
            }
        }
        is EnumRef -> List(paramCount(name)) { i -> variants(name).flatMap { nameOccurrences(it.tag).getOrElse(i) { emptySet() } }.toSet() }
    }

    private fun computeKey(key: QSymbol): List<Set<Boolean>> {
        val kt = keyType(key)
        return kt.params.map { p -> buildSet { occurrences(p, kt.type, true, this) } }
    }

    private fun settle() {
        do {
            grew = false
            var changed = false
            for (n in nameOcc.keys.toList()) computeName(n).let { if (it != nameOcc[n]) { nameOcc[n] = it; changed = true } }
            for (k in keyOcc.keys.toList()) computeKey(k).let { if (it != keyOcc[k]) { keyOcc[k] = it; changed = true } }
        } while (changed || grew)
    }

    fun paramVariance(name: Name): List<Variance> {
        nameOccurrences(name)
        settle()
        return nameOcc.getValue(name).map(::variance)
    }

    fun keyVariance(key: QSymbol): List<Variance> {
        keyOccurrences(key)
        settle()
        return keyOcc.getValue(key).map(::variance)
    }

    companion object {
        val EMPTY get() = TypeCtx()
    }
}
