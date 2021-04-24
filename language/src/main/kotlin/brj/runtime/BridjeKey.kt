package brj.runtime

import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeKey(val key: String) : TruffleObject {
    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    class Execute {
        companion object {
            private val interop = InteropLibrary.getUncached()

            @Specialization
            @JvmStatic
            fun doExecute(_this: BridjeKey, args: Array<Any>): Any =
                if (args.size == 1) {
                    interop.readMember(args[0], _this.key)
                } else {
                    throw ArityException.create(1, args.size)
                }
        }
    }

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") _allowSideEffects: Boolean) = ":$key"
}