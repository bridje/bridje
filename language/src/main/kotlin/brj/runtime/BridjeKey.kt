package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeKey(val ns: Symbol, val name: Symbol) : TruffleObject {

    companion object {
        private val INTEROP = InteropLibrary.getUncached()
    }

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    @Throws(ArityException::class)
    fun execute(arguments: Array<Any?>): Any? {
        if (arguments.size != 1) {
            throw ArityException.create(1, 1, arguments.size)
        }
        val record = arguments[0]
        return INTEROP.readMember(record, name.name)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = "$name"
}

@ExportLibrary(InteropLibrary::class)
class BridjeOptionalKey(val key: BridjeKey) : TruffleObject {

    companion object {
        private val INTEROP = InteropLibrary.getUncached()
    }

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    @Throws(ArityException::class)
    fun execute(arguments: Array<Any?>): Any? {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val record = arguments[0]
        val memberName = key.name.name
        return if (INTEROP.isMemberReadable(record, memberName))
            INTEROP.readMember(record, memberName)
        else BridjeNull
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = "?${key.name}"
}
