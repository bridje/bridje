package brj

class Foo {
    companion object {
        @JvmStatic
        fun isZero(foo: Int) = foo == 0

        @JvmStatic
        fun dec(foo: Int) = foo - 1

        @JvmStatic
        fun conj(list: BridjeVector, el: Int) = list + el

        @JvmStatic
        fun conj(set: BridjeSet, el: Any) = set + el

        @JvmStatic fun plus(a: Long, b: Long) = a + b
    }
}
