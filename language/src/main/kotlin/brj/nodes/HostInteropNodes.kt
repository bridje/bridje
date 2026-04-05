package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.RootNode

class HostStaticMethodInvokeNode(
    language: BridjeLanguage,
    @field:CompilationFinal private val hostClass: TruffleObject,
    @field:CompilationFinal private val methodName: String,
) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? =
        interop.invokeMember(hostClass, methodName, *frame.arguments)
}

class HostInstanceMethodInvokeNode(
    language: BridjeLanguage,
    @field:CompilationFinal private val methodName: String,
) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? {
        val receiver = frame.arguments[0]
        val args = frame.arguments.copyOfRange(1, frame.arguments.size)
        return interop.invokeMember(receiver, methodName, *args)
    }
}

class HostInstanceFieldReadNode(
    language: BridjeLanguage,
    @field:CompilationFinal private val fieldName: String,
) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? =
        interop.readMember(frame.arguments[0], fieldName)
}
