package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeMacro(val fn: Any) : TruffleObject {

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "<macro>"
}
