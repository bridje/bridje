package brj.runtime

import com.oracle.truffle.api.interop.UnknownIdentifierException

/**
 * The Bridje-side analogue of `TruffleObject`: a value that participates in
 * Bridje field access via `BridjeKey`. Implementers route key lookup directly
 * inside the language runtime rather than through Truffle interop.
 */
interface BridjeObject {
    fun hasKey(key: BridjeKey): Boolean

    @Throws(UnknownIdentifierException::class)
    fun readKey(key: BridjeKey): Any
}
