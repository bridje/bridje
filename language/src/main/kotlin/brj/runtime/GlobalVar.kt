package brj.runtime

import brj.MonoType
import com.oracle.truffle.api.utilities.CyclicAssumption
import java.util.*

class GlobalVar {
    val sym: Symbol
    val type: MonoType
    val assumption = CyclicAssumption("GlobalVar value")
    var value: Any? set(value) {
        assumption.invalidate()
        field = value
    }

    constructor(sym: Symbol, type: MonoType, value: Any?) {
        this.sym = sym
        this.type = type
        this.value = value
    }

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVar -> false
        else -> this.sym == other.sym
    }

    override fun hashCode() = Objects.hash(sym)
}
