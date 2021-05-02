package brj.builtins

import brj.BridjeTypesGen.expectInteger
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
object GreaterThanFn : TruffleObject {
    @get:ExportMessage
    val isExecutable = true

    @ExportMessage
    fun execute(args: Array<*>) = expectInteger(args[0]) > expectInteger(args[1])
}