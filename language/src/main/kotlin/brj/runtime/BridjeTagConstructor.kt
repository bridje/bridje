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
    val arity: Int,
    val fieldNames: List<String>
) : TruffleObject {

    private val tagString: TruffleString = TruffleString.fromConstant(tag, TruffleString.Encoding.UTF_8)

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun isInstantiable() = true

    @ExportMessage
    @Throws(ArityException::class)
    fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != arity) {
            throw ArityException.create(arity, arity, arguments.size)
        }
        return BridjeTaggedTuple(this, arguments.map { it!! }.toTypedArray())
    }

    @ExportMessage
    @Throws(ArityException::class)
    fun instantiate(arguments: Array<Any?>): Any {
        if (arguments.size != arity) {
            throw ArityException.create(arity, arity, arguments.size)
        }
        return BridjeTaggedTuple(this, arguments.map { it!! }.toTypedArray())
    }

    @ExportMessage
    fun isMetaObject() = true

    @ExportMessage
    fun getMetaSimpleName(): Any = tagString

    @ExportMessage
    fun getMetaQualifiedName(): Any = tagString

    @ExportMessage
    @TruffleBoundary
    fun isMetaInstance(instance: Any?): Boolean = when (instance) {
        is BridjeTaggedTuple -> instance.constructor === this
        else -> false
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = tag
}
