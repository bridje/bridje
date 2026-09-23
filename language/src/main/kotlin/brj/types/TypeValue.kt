package brj.types

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

// A type as a runtime value, as in a var's `declaredType` meta.
@ExportLibrary(InteropLibrary::class)
class TypeValue(val type: Type) : TruffleObject {
    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()

    override fun toString(): String = Rendered(type).toString()
}
