package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.BridjeFuture
import brj.runtime.TaskScope
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.RootNode
import java.util.concurrent.CountDownLatch

class SpawnNode(language: BridjeLanguage) : RootNode(language) {

    override fun execute(frame: VirtualFrame): Any {
        val fn = frame.arguments[0]
        return doSpawn(fn)
    }

    @TruffleBoundary
    private fun doSpawn(fn: Any): BridjeFuture {
        val interop = InteropLibrary.getUncached()
        val ctx = BridjeContext.get(this)
        val parentScope = TaskScope.current() ?: ctx.rootScope
        val childScope = TaskScope(parent = parentScope)
        val registered = CountDownLatch(1)

        val future = ctx.executor.submit<Any?> {
            childScope.ownerThread = Thread.currentThread()
            registered.await() // ensure handle is registered before we can call childFailed
            ScopedValue.callWhere(TaskScope.SCOPED_VALUE, childScope) {
                try {
                    val result = interop.execute(fn)
                    childScope.joinChildren()
                    childScope.childFailure.get()?.let { throw it }
                    result
                } catch (e: Throwable) {
                    childScope.cancelChildren()
                    val realCause = childScope.childFailure.get() ?: e
                    parentScope.childFailed(childScope, realCause)
                    throw realCause
                }
            }
        }

        val handle = parentScope.addChild(future, childScope)
        registered.countDown()
        return BridjeFuture(future, childScope)
    }
}
