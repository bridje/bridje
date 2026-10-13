package brj.types

internal val Type.tv: TypeVar get() = base as TypeVar
internal val Type.fn: Base.Fn get() = base as Base.Fn

// A record type as a literal has it: carrying no key it does not mention.
internal fun Type.closed(): Type = copy(base = (base as Base.Rec).copy(closed = true))

// The type as the simplifier renders it, its variables as they are.
internal fun Type.shown(env: BoundEnv, ctx: TypeCtx = TypeCtx.EMPTY): Type = Scheme(this, env).simplify(ctx).type
