package brj.runtime

import brj.GlobalVar
import com.oracle.truffle.api.interop.TruffleObject

class BridjeFxMap(private val entries: Map<GlobalVar, Any?> = emptyMap()) : TruffleObject {

    operator fun get(key: GlobalVar): Any? = entries[key]

    fun assoc(overrides: Map<GlobalVar, Any?>): BridjeFxMap =
        BridjeFxMap(entries + overrides)

    companion object {
        val EMPTY = BridjeFxMap()
    }
}
