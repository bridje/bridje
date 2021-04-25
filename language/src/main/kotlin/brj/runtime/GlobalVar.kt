package brj.runtime

import brj.Typing
import java.util.*

internal sealed class GlobalVar {
    abstract val sym: Symbol
    abstract val typing: Typing
    abstract val bridjeVar: BridjeVar

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVar -> false
        else -> this.sym == other.sym
    }

    override fun hashCode() = Objects.hash(sym)
}

internal class DefVar(override val sym: Symbol, override val typing: Typing, override val bridjeVar: BridjeVar) :
    GlobalVar()

internal class DefxVar(
    override val sym: Symbol,
    override val typing: Typing,
    override val bridjeVar: BridjeVar,
    val defaultImpl: BridjeVar
) : GlobalVar()
