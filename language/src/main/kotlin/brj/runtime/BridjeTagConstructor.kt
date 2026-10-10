package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
class BridjeTagConstructor(
    val tag: String,
    override val keys: List<QSymbol>,
) : TruffleObject, TagConstructor {

    private val tagString: TruffleString = TruffleString.fromConstant(tag, TruffleString.Encoding.UTF_8)

    @TruffleBoundary
    private fun construct(arguments: Array<Any?>): Any {
        val argument = arguments.fitted(1)[0]
        val record = argument as? BridjeRecord
            ?: throw Anomaly.incorrect("$tag expects a record, got ${INTEROP.toDisplayString(argument)}")
        val missing = keys.filterNot { record.hasKey(it) }
        if (missing.isNotEmpty())
            throw Anomaly.incorrect("$tag requires ${missing.joinToString(", ") { it.toDisplayString() }}")
        return record.withTag(this)
    }

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun isInstantiable() = true

    @ExportMessage
    @Throws(ArityException::class)
    fun execute(arguments: Array<Any?>): Any = construct(arguments)

    @ExportMessage
    @Throws(ArityException::class)
    fun instantiate(arguments: Array<Any?>): Any = construct(arguments)

    @ExportMessage
    fun isMetaObject() = true

    @ExportMessage
    fun getMetaSimpleName(): Any = tagString

    @ExportMessage
    fun getMetaQualifiedName(): Any = tagString

    @ExportMessage
    fun isMetaInstance(instance: Any?): Boolean =
        instance is BridjeRecord && instance.tag === this

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = tag

    private companion object {
        private val INTEROP = InteropLibrary.getUncached()
    }
}
