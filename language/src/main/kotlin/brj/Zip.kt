package brj

internal interface Zippable<E : Zippable<E>> {
    val children: List<E>
}

internal val <E : Zippable<E>> E.zip get() = ZipRoot(this)

internal sealed interface Zip<E : Zippable<E>> {
    val znode: E
    val zdown: Zip<E>? get() = znode.children.firstOrNull()?.let { ZipNode(it, this, 0) }
    val zright: Zip<E>?
    val zleft: Zip<E>?
    val zup: Zip<E>?

    val zchildren get() = znode.children.mapIndexed { idx, child -> ZipNode(child, this, idx) }
}

internal val <E : Zippable<E>> Zip<E>?.zrights get() = generateSequence(this) { it.zright }

internal class ZipRoot<E : Zippable<E>>(override val znode: E) : Zip<E> {
    override val zright: Zip<E>? = null
    override val zleft: Zip<E>? = null
    override val zup: Zip<E>? = null
}

internal class ZipNode<E : Zippable<E>>(override val znode: E, override val zup: Zip<E>, val siblingIdx: Int) : Zip<E> {
    private fun sibling(idx: Int) = zup.znode.children.getOrNull(idx)?.let { ZipNode(it, zup, idx) }

    override val zright: Zip<E>? get() = sibling(siblingIdx + 1)
    override val zleft: Zip<E>? get() = sibling(siblingIdx - 1)
}