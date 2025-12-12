package brj

import com.oracle.truffle.api.interop.TruffleObject

class GlobalVar(val name: String, val value: Any?) : TruffleObject {
    override fun toString(): String = "#'$name"
}
