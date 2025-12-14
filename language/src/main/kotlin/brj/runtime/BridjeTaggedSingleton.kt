package brj.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
class BridjeTaggedSingleton(
    val tag: String
) : TruffleObject {

    private val tagString: TruffleString = TruffleString.fromConstant(tag, TruffleString.Encoding.UTF_8)

    @ExportMessage
    fun hasMetaObject() = true

    @ExportMessage
    fun getMetaObject(): Any = this

    @ExportMessage
    fun isMetaObject() = true

    @ExportMessage
    fun getMetaSimpleName(): Any = tagString

    @ExportMessage
    fun getMetaQualifiedName(): Any = tagString

    @ExportMessage
    fun isMetaInstance(instance: Any?) = instance === this

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean): String = tag
}
