package brj.types

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class TypeValue(val scheme: Scheme) : TruffleObject {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()

    override fun toString(): String = scheme.type.toString()
}
