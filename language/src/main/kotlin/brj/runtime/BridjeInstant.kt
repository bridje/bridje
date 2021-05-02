package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@ExportLibrary(InteropLibrary::class)
class BridjeInstant(private val instant: Instant) : TruffleObject {
    @get:ExportMessage
    val isDate = true

    @get:ExportMessage
    val isTime = true

    @get:ExportMessage
    val isTimeZone = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun asDate() = LocalDate.ofInstant(instant, ZoneId.systemDefault())

    @ExportMessage
    fun asTime() = LocalTime.ofInstant(instant, ZoneId.systemDefault())

    @ExportMessage
    fun asTimeZone() = ZoneId.systemDefault()

    @ExportMessage
    fun asInstant() = instant

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = """(#inst "$instant")"""
}