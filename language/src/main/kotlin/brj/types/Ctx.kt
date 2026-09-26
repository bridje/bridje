package brj.types

import brj.GlobalVar
import brj.runtime.QSymbol

sealed interface FieldType {
    // The field's type is the tag's n-th type argument.
    data class Param(val index: Int) : FieldType
    // The field's type is not tracked: fresh at every instantiation.
    data object Untracked : FieldType
    // The field has one type, with no variables of the tag's.
    data class Fixed(val type: Type) : FieldType
    // The field is a declared key of a record-style tag: it has the key's type.
    data class Key(val key: QSymbol) : FieldType
}

sealed interface Payload {
    data object None : Payload
    data class Positional(val fields: List<FieldType>) : Payload
    data class Record(val keys: Set<QSymbol>) : Payload
}

// What the checker knows about a declared tag. A tag inside an enum shares the enum's type parameters.
data class TagInfo(val tag: TagRef, val enum: EnumRef?, val paramCount: Int, val payload: Payload)

// What the checker needs from the world outside the expression: declared key types, tag declarations
// and enum membership. Until the analyser records these itself they are supplied by the caller.
class TypeCtx(
    private val keyTypes: (QSymbol) -> Type? = { null },
    // Keyed by the runtime constructor or singleton object, which is what the analyser puts in the Expr.
    private val tagsByValue: Map<Any, TagInfo> = emptyMap(),
    private val variantsByEnum: Map<EnumRef, List<TagRef>> = emptyMap(),
    // Schemes of globals, over and above what the globals themselves carry.
    private val globalSchemes: (GlobalVar) -> Scheme? = { null },
) {
    fun globalScheme(gv: GlobalVar): Scheme? = globalSchemes(gv) ?: gv.scheme

    private val tagsByRef = tagsByValue.values.associateBy { it.tag }

    fun keyType(key: QSymbol): Type = keyTypes(key) ?: TypeVar()

    fun tagInfo(value: Any): TagInfo =
        tagsByValue[value] ?: throw TypeCheckException("unknown tag $value")

    fun tagInfo(ref: TagRef): TagInfo =
        tagsByRef[ref] ?: throw TypeCheckException("unknown tag $ref")

    fun variants(enum: EnumRef): List<TagInfo> =
        (variantsByEnum[enum] ?: throw TypeCheckException("unknown enum $enum")).map { tagInfo(it) }

    // The keys a value of this type is known to carry, or null if it is not record-kind.
    // A record-payload tag carries its payload's keys; an enum carries what every variant carries.
    fun recordKeys(t: Type): Set<QSymbol>? = when (t) {
        is RecordType -> t.keys
        is TagType -> (tagInfo(t.tag).payload as? Payload.Record)?.keys
        is EnumType -> {
            val perVariant = variants(t.enum).map { (it.payload as? Payload.Record)?.keys ?: return null }
            perVariant.reduceOrNull { a, b -> a intersect b } ?: emptySet()
        }
        is Meet -> when {
            t.filter.record -> (recordKeys(t.base) ?: if (t.base is TypeVar) emptySet() else return null) + t.filter.keys
            else -> recordKeys(t.base)
        }
        else -> null
    }

    fun isNominal(t: Type) = t is TagType || t is EnumType

    companion object {
        val EMPTY get() = TypeCtx()
    }
}
