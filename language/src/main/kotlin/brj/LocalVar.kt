package brj

import brj.runtime.Symbol

class LocalVar(val symbol: Symbol) {
    override fun toString() = "$symbol"
}