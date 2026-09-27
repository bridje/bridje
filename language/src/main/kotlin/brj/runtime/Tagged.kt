package brj.runtime

/**
 * A tag's constructor: a name over a record carrying (at least) [keys].
 */
interface TagConstructor {
    val keys: List<QSymbol>
}

/**
 * A value that is a tag's name over one record, [payload].
 *
 * A `case` pattern binds or destructures the payload, and key lookup reads through to it.
 */
interface Tagged : BridjeObject {
    val payload: BridjeRecord

    override fun hasKey(key: BridjeKey): Boolean = payload.hasKey(key)

    override fun readKey(key: BridjeKey): Any = payload.readKey(key)
}
