package brj.runtime

import com.oracle.truffle.api.utilities.CyclicAssumption

class BridjeVar(value: Any?) {
    val assumption = CyclicAssumption("BridjeVar value")

    var value: Any? = value
        private set

    fun set(value: Any) {
        assumption.invalidate()
        this.value = value
    }
}