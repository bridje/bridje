package brj.builtins

import brj.BridjeLanguage
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.strings.TruffleString

class BytesCountNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doCount(frame.arguments[0])

    @TruffleBoundary
    private fun doCount(arg: Any): Long {
        val arr = BridjeContext.get(this).truffleEnv.asHostObject(arg) as ByteArray
        return arr.size.toLong()
    }
}

class BytesNthNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doNth(frame.arguments[0], frame.arguments[1] as Long)

    @TruffleBoundary
    private fun doNth(arg: Any, index: Long): Long {
        val arr = BridjeContext.get(this).truffleEnv.asHostObject(arg) as ByteArray
        if (index < 0 || index >= arr.size) throw incorrect("Bytes/nth: index out of bounds: $index")
        return (arr[index.toInt()].toInt() and 0xFF).toLong()
    }
}

class BytesFromStrNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doConvert(frame.arguments[0] as TruffleString)

    @TruffleBoundary
    private fun doConvert(s: TruffleString): Any =
        BridjeContext.get(this).truffleEnv.asGuestValue(s.toJavaStringUncached().toByteArray(Charsets.UTF_8))
}

class StrFromBytesNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doConvert(frame.arguments[0])

    @TruffleBoundary
    private fun doConvert(arg: Any): TruffleString {
        val arr = BridjeContext.get(this).truffleEnv.asHostObject(arg) as ByteArray
        return TruffleString.fromJavaStringUncached(String(arr, Charsets.UTF_8), TruffleString.Encoding.UTF_8)
    }
}
