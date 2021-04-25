package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeVariant(val variantKey: BridjeKey, val record: BridjeRecord) : TruffleObject {

    private val variantKeys = InteropLibrary.getUncached(variantKey)
    private val records = InteropLibrary.getUncached(record)

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "(${variantKeys.toDisplayString(variantKey)} ${records.toDisplayString(record, allowSideEffects)})"

    @ExportMessage
    fun hasMetaObject() = true

    @ExportMessage
    fun getMetaObject() = variantKey

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean) =
        records.getMembers(record, includeInternal)

    @ExportMessage
    fun isMemberReadable(member: String) =
        records.isMemberReadable(record, member)

    @ExportMessage
    fun readMember(member: String) =
        records.readMember(record, member)
}