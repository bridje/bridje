package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@ExportLibrary(InteropLibrary::class)
class BridjeFuture(
    private val delegate: Future<Any?>,
    private val interruptedCtor: BridjeTagConstructor
) : Future<Any?> by delegate, TruffleObject {

    @ExportMessage
    fun hasMembers() = false

    @ExportMessage
    fun getMembers(@Suppress("UNUSED_PARAMETER") includeInternal: Boolean): Any =
        throw UnsupportedMessageException.create()

    @TruffleBoundary
    override fun get(): Any? {
        try {
            return delegate.get() ?: BridjeNull
        } catch (e: ExecutionException) {
            rethrowCause(e)
        } catch (e: CancellationException) {
            rethrowCause(e)
        }
    }

    @TruffleBoundary
    override fun get(timeout: Long, unit: TimeUnit): Any? {
        try {
            return delegate.get(timeout, unit) ?: BridjeNull
        } catch (e: ExecutionException) {
            rethrowCause(e)
        } catch (e: CancellationException) {
            rethrowCause(e)
        }
    }

    private fun rethrowCause(e: Exception): Nothing {
        val cause = if (e is ExecutionException) (e.cause ?: e) else e
        if (cause is BridjeException) throw cause
        if (cause is CancellationException || cause is InterruptedException) {
            val data = BridjeRecord.EMPTY.put("exnMessage", "Task was interrupted")
            throw BridjeException(BridjeTaggedTuple(interruptedCtor, arrayOf(data)), cause)
        }
        throw BridjeException(cause.message ?: "Task failed")
    }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) =
        if (isDone) "#<Future: done>" else "#<Future: pending>"
}
