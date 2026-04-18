package brj.runtime

import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.strings.TruffleString

@ExportLibrary(InteropLibrary::class)
abstract class BuiltinMetaObj(val tagName: String, val ns: String) : TruffleObject {
    private val fqName: String = "$ns/$tagName"
    private val tagString: TruffleString = TruffleString.fromConstant(tagName, TruffleString.Encoding.UTF_8)
    private val fqTagString: TruffleString = TruffleString.fromConstant(fqName, TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = tagString
    @ExportMessage fun getMetaQualifiedName(): Any = fqTagString

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = fqName

    @ExportMessage fun isExecutable() = true
    @ExportMessage fun isInstantiable() = true

    @ExportMessage
    @Throws(ArityException::class)
    abstract fun execute(arguments: Array<Any?>): Any

    @ExportMessage
    @Throws(ArityException::class)
    open fun instantiate(arguments: Array<Any?>): Any = execute(arguments)

    @ExportMessage
    abstract fun isMetaInstance(instance: Any?): Boolean
}
