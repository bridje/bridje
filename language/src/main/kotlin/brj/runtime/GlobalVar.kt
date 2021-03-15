package brj.runtime

import brj.MonoType
import java.util.*

class GlobalVar(val sym: Symbol, val type: MonoType, val value: Any) {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVar -> false
        else -> this.sym == other.sym
    }

    override fun hashCode() = Objects.hash(sym)
}