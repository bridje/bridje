package brj.runtime

import com.oracle.truffle.api.interop.ArityException

// The call convention every callable keeps: a missing trailing argument is the empty record, a surplus one is an
// arity error.
fun Array<Any?>.fitted(arity: Int): Array<Any?> = when {
    size == arity -> this
    size > arity -> throw ArityException.create(arity, arity, size)
    else -> Array(arity) { getOrElse(it) { BridjeRecord.EMPTY } }
}
