package brj.types

internal val Type.tv: TypeVar get() = base as TypeVar
internal val Type.fn: Base.Fn get() = base as Base.Fn

internal fun Type.closed(): Type = copy(base = (base as Base.Rec).copy(closed = true))
