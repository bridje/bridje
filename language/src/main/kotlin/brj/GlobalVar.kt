package brj

import brj.runtime.BridjeRecord
import brj.runtime.Symbol
import brj.types.Type
import com.oracle.truffle.api.interop.TruffleObject

class GlobalVar(val name: Symbol, val value: Any?, val meta: BridjeRecord = BridjeRecord.EMPTY, val type: Type? = null, val effects: List<GlobalVar> = emptyList()) : TruffleObject {
    override fun toString(): String = "#'${name.name}"
}
