package brj.builtins

import brj.BridjeLanguage
import brj.Form
import brj.Reader.Companion.readForms
import brj.runtime.BridjeFile
import brj.runtime.BridjeVector
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.strings.TruffleString

class FormsFromFileNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any = doRead(frame.arguments[0] as BridjeFile)

    @TruffleBoundary
    private fun doRead(f: BridjeFile): BridjeVector {
        val source = Source.newBuilder("bridje", f.truffleFile).build()
        val forms: List<Form> = source.readForms().toList()
        return BridjeVector(forms)
    }
}

class FormsFromStringNode(language: BridjeLanguage) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val str = (frame.arguments[0] as TruffleString).toJavaStringUncached()
        return doRead(str)
    }

    @TruffleBoundary
    private fun doRead(str: String): BridjeVector {
        val source = Source.newBuilder("bridje", str, "<forms/<-str>").build()
        val forms: List<Form> = source.readForms().toList()
        return BridjeVector(forms)
    }
}
