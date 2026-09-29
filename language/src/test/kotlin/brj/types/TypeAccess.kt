package brj.types

internal val Type.tv: TypeVar get() = base as TypeVar
internal val Type.fn: Base.Fn get() = base as Base.Fn

// A record type as a literal has it: carrying no key it does not mention.
internal fun Type.closed(): Type = copy(base = (base as Base.Rec).copy(closed = true))
