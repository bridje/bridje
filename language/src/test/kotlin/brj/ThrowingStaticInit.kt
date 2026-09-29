package brj

class ThrowingStaticInit {
    companion object {
        init {
            throw RuntimeException("static initialiser ran")
        }
    }
}
