package brj

import brj.runtime.BridjeRecord
import com.oracle.truffle.api.interop.TruffleObject

class GlobalVar(val name: String, val value: Any?, val meta: BridjeRecord = BridjeRecord.EMPTY) : TruffleObject {
    override fun toString(): String = "#'$name"
}
