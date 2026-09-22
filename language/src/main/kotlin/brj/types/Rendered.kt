package brj.types

// A scheme reduced for display. Variables determined by their bounds are replaced; variables that always
// travel together are one variable; what remains is named, and the bounds that still carry information
// are listed as constraints in the leading vector, `^(lower, upper)`. The spelling is a placeholder (Q8).
class Rendered(
    val type: Type,
    val constraints: List<Pair<Type, Type>>,
    private val names: Map<TypeVar, String>,
) {
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
        is Meet -> when {
            t.filter.record -> "${render(t.base)}${RecordType(t.filter.keys)}"
            else -> "${render(t.base)} ∧ ${t.filter}"
        }
        is PrimType, is RecordType, BottomType, NilType -> t.toString()
    }

    override fun toString(): String {
        val head = names.values + constraints.map { (l, u) -> "^(${render(l)}, ${render(u)})" }
        return if (head.isEmpty()) render(type) else "[${head.joinToString(", ")}] ${render(type)}"
    }
}

internal fun varName(i: Int): String {
    val letter = ('a' + i % 26).toString()
    return if (i < 26) letter else letter + (i / 26)
}
