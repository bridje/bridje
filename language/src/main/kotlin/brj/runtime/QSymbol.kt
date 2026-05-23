package brj.runtime

/**
 * A qualified identifier — `ns/name`. The runtime layer's analogue of the
 * reader's `QSymbolForm` / `QKeywordForm`. Used for record field identity:
 * `{:foo 1}` declared in `brj.test` and `{:bar/foo 1}` declared in `bar`
 * coexist in one record because their `QSymbol` keys are distinct.
 *
 * Data-class equality on `(ns, name)` is what makes this work as a
 * `DynamicObjectLibrary` key — distinct `QSymbol` instances with the same
 * `(ns, name)` compare and hash equal.
 */
data class QSymbol(val ns: Symbol, val name: Symbol) {
    override fun toString(): String = "${ns.name}/${name.name}"

    companion object {
        /**
         * Split a polyglot member name like `"brj.test/test"` into a [QSymbol].
         * Returns `null` if the string has no `/` — the caller decides how to
         * handle that (typically: fall through to an unqualified-name scan).
         */
        fun parse(s: String): QSymbol? {
            val slash = s.indexOf('/')
            if (slash < 0) return null
            return QSymbol(Symbol.intern(s.substring(0, slash)), Symbol.intern(s.substring(slash + 1)))
        }
    }
}
