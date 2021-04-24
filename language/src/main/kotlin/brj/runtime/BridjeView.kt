package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.time.Instant

@ExportLibrary(value = InteropLibrary::class, delegateTo = "obj")
class BridjeView(@JvmField val obj: Any) : TruffleObject {
    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @TruffleBoundary
    private fun longToString(l: Long) = l.toString()

    private val interop = InteropLibrary.getUncached()

    @TruffleBoundary
    private fun arrayToString(arrays: InteropLibrary, array: Any): String {
        return (0 until arrays.getArraySize(array))
            .joinToString(prefix = "(Array ", separator = ", ", postfix = ")") {
                toDisplayString(arrays.readArrayElement(array, it))
            }
    }

    @TruffleBoundary
    private fun objToString(objLib: InteropLibrary, obj: Any): String {
        val ks = objLib.getMembers(obj)
        val membersStr = (0 until interop.getArraySize(ks))
            .joinToString(prefix = "{", separator = ", ", postfix = "}") { idx ->
                val k = interop.asString(interop.readArrayElement(ks, idx))
                "$k, ${toDisplayString(interop.readMember(obj, k))}"
            }

        return if (objLib.hasMetaObject(obj))
            "(${toDisplayString(interop, objLib.getMetaObject(obj))} $membersStr)"
        else membersStr
    }

    @TruffleBoundary
    private fun instToString(inst: Instant) = """(#inst "$inst")"""

    @TruffleBoundary
    private fun fnToString(objLib: InteropLibrary, obj: Any) =
        "#<fn ${interop.asString(objLib.toDisplayString(obj))}>"

    @TruffleBoundary
    private fun fallbackToString(objLib: InteropLibrary, obj: Any) =
        if (objLib.hasMetaObject(obj)) {
            """#<${toDisplayString(objLib.getMetaObject(obj))} @${System.identityHashCode(obj)} "$obj">"""
        } else {
            """#<@${System.identityHashCode(obj)} "$obj">"""
        }

    @ExportMessage.Ignore
    private fun toDisplayString(objLib: InteropLibrary, obj: Any): String = when {
        objLib.isNull(obj) -> "nil"
        objLib.isBoolean(obj) -> objLib.asBoolean(obj).toString()
        objLib.fitsInLong(obj) -> longToString(objLib.asLong(obj))
        objLib.isString(obj) -> objLib.asString(obj)
        objLib.isInstant(obj) -> instToString(objLib.asInstant(obj))
        objLib.isMetaObject(obj) -> interop.asString(objLib.getMetaQualifiedName(obj))
        objLib.isExecutable(obj) -> fnToString(objLib, obj)
        objLib.hasArrayElements(obj) -> arrayToString(objLib, obj)
        else -> fallbackToString(objLib, obj)
    }

    @ExportMessage.Ignore
    private fun toDisplayString(obj: Any) = toDisplayString(interop, obj)

    @ExportMessage
    fun toDisplayString(
        _allowSideEffects: Boolean,
        @CachedLibrary("this.obj") objLib: InteropLibrary
    ): String =
        toDisplayString(objLib, obj)
}