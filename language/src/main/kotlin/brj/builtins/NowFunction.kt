package brj.builtins

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
object NowFunction : TruffleObject {
    @get:ExportMessage
    val isExecutable = true

    @TruffleBoundary
    private fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    @ExportMessage
    fun execute(args: Array<*>) = currentTimeMillis()
}