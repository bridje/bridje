package brj.runtime

interface Meta<M : Meta<M>> {
    val meta: BridjeRecord
    fun withMeta(newMeta: BridjeRecord?): M
}
