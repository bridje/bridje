package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeKey(val name: String) : TruffleObject {

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
        return INTEROP.readMember(record, name)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = name
}
