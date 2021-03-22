package brj.runtime

import brj.MonoType
import java.util.*

sealed class GlobalVar {
    abstract val sym: Symbol
    abstract val type: MonoType
    abstract val bridjeVar: BridjeVar

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVar -> false
        else -> this.sym == other.sym
    }

    override fun hashCode() = Objects.hash(sym)
}

class DefVar(override val sym: Symbol, override val type: MonoType, override val bridjeVar: BridjeVar) : GlobalVar()

class DefxVar(override val sym: Symbol, override val type: MonoType, override val bridjeVar: BridjeVar, val defaultImpl: BridjeVar) : GlobalVar()
