package brj

internal class Symbol private constructor(val local: String) {
    companion object {
        private val SYMBOLS = mutableMapOf<String, Symbol>()
        internal fun symbol(local: String) = SYMBOLS.computeIfAbsent(local, ::Symbol)
    }
}

