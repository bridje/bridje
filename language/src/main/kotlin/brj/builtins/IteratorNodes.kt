package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.RootNode

class ItrNode(language: BridjeLanguage) : RootNode(language) {
    @Child private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        return doGetIterator(frame.arguments[0])
    }

    @TruffleBoundary
    private fun doGetIterator(obj: Any): Any = interop.getIterator(obj)
}

class ItrHasNextNode(language: BridjeLanguage) : RootNode(language) {
    @Child private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        return doHasNext(frame.arguments[0])
    }

    @TruffleBoundary
    private fun doHasNext(iter: Any): Any = interop.hasIteratorNextElement(iter)
}

class ItrNextNode(language: BridjeLanguage) : RootNode(language) {
    @Child private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        return doNext(frame.arguments[0])
    }

    @TruffleBoundary
    private fun doNext(iter: Any): Any = interop.getIteratorNextElement(iter)
}
