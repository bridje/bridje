package brj.types

// A scheme reduced for display. Variables determined by their bounds are replaced; variables that always
// travel together are one variable; what remains is named, and the bounds that still carry information
// are listed as constraints in the leading vector, `isa(lower, upper)`.
class Rendered(
    val type: Type,
    val constraints: List<Pair<Type, Type>>,
    private val names: Map<TypeVar, String>,
) {
    fun render(t: Type): String = if (t.nullable) "${render(t.base)}?" else render(t.base)

    private fun render(b: Base): String = when (b) {
        is TypeVar -> names[b] ?: "?"
        is Base.Fn -> "Fn([${b.paramTypes.joinToString(", ") { render(it) }}] ${render(b.returnType)})"
        is Base.Vector -> "[${render(b.el)}]"
        is Base.Set -> "#{${render(b.el)}}"
        is Base.Iterable -> "Iterable(${render(b.el)})"
        is Base.Iterator -> "Iterator(${render(b.el)})"
        is Base.Host -> if (b.args.isEmpty()) b.toString() else "${b.className.substringAfterLast('.')}(${b.args.joinToString(", ") { render(it) }})"
        is Base.Rec -> {
            val withArgs = b.name?.let { n -> if (b.args.isEmpty()) "$n" else "$n(${b.args.joinToString(", ") { render(it) }})" }
            when {
                withArgs == null -> "{${renderSlots(b.keys)}}"
                b.keys.isEmpty() -> withArgs
                else -> "$withArgs{${renderSlots(b.keys)}}"
            }
        }
        is Base.OnVar -> if (b.keys.isEmpty()) "{& ${render(b.base)}}" else "{${renderSlots(b.keys)} & ${render(b.base)}}"
        is Base.Prim, Base.Nothing -> b.toString()
    }

    // A slot as a record type form writes it: `.k`, `.?k`, and its instance where the key has type variables, `.k(Int)`.
    private fun renderSlots(keys: Map<brj.runtime.QSymbol, Slot>): String = keys.entries.joinToString(", ") { (k, s) ->
        slotString(k, s.copy(args = emptyList())) + if (s.args.isEmpty()) "" else "(${s.args.joinToString(", ") { render(it) }})"
    }

    override fun toString(): String {
        val head = names.values + constraints.map { (l, u) -> "isa(${render(l)}, ${render(u)})" }
        return if (head.isEmpty()) render(type) else "[${head.joinToString(", ")}] ${render(type)}"
    }
}

internal fun varName(i: Int): String {
    val letter = ('a' + i % 26).toString()
    return if (i < 26) letter else letter + (i / 26)
}
