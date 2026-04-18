package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeFile(@JvmField val truffleFile: TruffleFile) : TruffleObject {

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = FileMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return truffleFile
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = "File(${truffleFile.path})"
}

object FileMeta : BuiltinMetaObj("File", "brj.fs") {
    override fun isMetaInstance(instance: Any?) = instance is BridjeFile

    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw com.oracle.truffle.api.interop.ArityException.create(1, 1, arguments.size)
        return BridjeFile(arguments[0] as TruffleFile)
    }
}
