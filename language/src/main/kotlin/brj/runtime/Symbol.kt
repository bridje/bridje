package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.strings.TruffleString
import java.util.concurrent.ConcurrentHashMap

@ExportLibrary(InteropLibrary::class)
class Symbol private constructor(val name: String) : TruffleObject {

    private val nameTruffle: TruffleString =
        TruffleString.fromJavaStringUncached(name, TruffleString.Encoding.UTF_8)

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = SymbolMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return nameTruffle
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = name

    override fun toString(): String = name

    companion object {
        private val INTERNED = ConcurrentHashMap<String, Symbol>()

        @JvmStatic
        fun intern(name: String): Symbol = INTERNED.computeIfAbsent(name) { Symbol(it) }
    }
}

val String.sym: Symbol get() = Symbol.intern(this)

object SymbolMeta : BuiltinMetaObj("Symbol".sym, "brj.core".sym) {
    override fun isMetaInstance(instance: Any?) = instance is Symbol

    @Throws(ArityException::class)
    @TruffleBoundary
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val name = (arguments[0] as TruffleString).toJavaStringUncached()
        return Symbol.intern(name)
    }
}
