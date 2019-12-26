package brj.runtime

import brj.emitter.BridjeObject
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import org.graalvm.polyglot.Value

@ExportLibrary(InteropLibrary::class)
internal open class VariantObject(val variantKey: VariantKey, val args: Array<Any?>) : BridjeObject {

    private val paramCount = variantKey.paramTypes.size
    @CompilerDirectives.TruffleBoundary
    override fun toString(): String =
        if (variantKey.paramTypes.isEmpty())
            variantKey.sym.toString()
        else
            "(${variantKey.sym} ${variantKey.paramTypes
                .mapIndexed { idx, _ ->
                    val el = Value.asValue(args[idx])
                    if (el.isHostObject) el.asHostObject<Any>().toString() else el.toString()
                }
                .joinToString(" ")})"

    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = paramCount

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx < paramCount

    @ExportMessage
    fun readArrayElement(idx: Long) =
        if (isArrayElementReadable(idx)) args[idx.toInt()]
        else {
            CompilerDirectives.transferToInterpreter()
            throw IndexOutOfBoundsException()
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VariantObject) return false

        if (variantKey != other.variantKey) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variantKey.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}