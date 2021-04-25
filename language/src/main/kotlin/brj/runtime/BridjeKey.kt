package brj.runtime

import brj.BridjeLanguage
import brj.BridjeTypesGen.expectBridjeRecord
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeKey(val key: Symbol) : TruffleObject {
    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

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
                    interop.readMember(args[0], _this.key.toString())
                } else {
                    throw ArityException.create(1, args.size)
                }
        }
    }

    @ExportMessage
    fun isMetaObject() = true

    @ExportMessage
    fun getMetaQualifiedName() = key.toString()

    @ExportMessage
    fun getMetaSimpleName() = key.toString()

    @ExportMessage
    fun isMetaInstance(obj: Any) = obj is BridjeVariant && obj.variantKey.key === key

    @ExportMessage
    fun isInstantiable() = true

    @ExportMessage
    fun instantiate(args: Array<Any>): BridjeVariant {
        if (args.size != 1) throw ArityException.create(1, args.size)
        return BridjeVariant(this, expectBridjeRecord(args[0]))
    }

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") _allowSideEffects: Boolean) = ":$key"
}