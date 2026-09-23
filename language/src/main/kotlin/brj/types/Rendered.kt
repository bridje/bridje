package brj.types

// A type for display, its variables named a, b, … in order of appearance.
class Rendered(val type: Type, private val names: Map<TypeVar, String>) {
    constructor(type: Type) : this(type, type.typeVars().withIndex().associate { (i, v) -> v to varName(i) })

    fun render(t: Type): String = when (t) {
        is TypeVar -> names[t] ?: "?"
        is NullableType -> "${render(t.inner)}?"
        is FnType -> "Fn([${t.paramTypes.joinToString(", ") { render(it) }}] ${render(t.returnType)})"
        is VectorType -> "[${render(t.el)}]"
        is SetType -> "#{${render(t.el)}}"
        is IterableType -> "Iterable(${render(t.el)})"
        is IteratorType -> "Iterator(${render(t.el)})"
        is HostType -> if (t.args.isEmpty()) t.toString() else "${t.className.substringAfterLast('.')}(${t.args.joinToString(", ") { render(it) }})"
        is TagType -> if (t.args.isEmpty()) "${t.tag}" else "${t.tag}(${t.args.joinToString(", ") { render(it) }})"
        is EnumType -> if (t.args.isEmpty()) "${t.enum}" else "${t.enum}(${t.args.joinToString(", ") { render(it) }})"
        is PrimType, is RecordType, NothingType -> t.toString()
    }

    override fun toString(): String =
        if (names.isEmpty()) render(type) else "[${names.values.joinToString(", ")}] ${render(type)}"
}

internal fun varName(i: Int): String {
    val letter = ('a' + i % 26).toString()
    return if (i < 26) letter else letter + (i / 26)
}
