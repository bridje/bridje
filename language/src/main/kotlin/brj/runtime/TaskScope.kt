package brj.runtime

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

enum class TaskScopeState { ACTIVE, CANCELLING }

class ChildHandle(
    val future: Future<Any?>,
    val scope: TaskScope
)

class TaskScope(val parent: TaskScope?) {

    val state = AtomicReference(TaskScopeState.ACTIVE)
    val children = ConcurrentLinkedQueue<ChildHandle>()
    @Volatile var ownerThread: Thread? = null
    val childFailure = AtomicReference<Throwable>(null)

    fun addChild(future: Future<Any?>, childScope: TaskScope): ChildHandle {
        val handle = ChildHandle(future, childScope)
        children.add(handle)
        if (state.get() == TaskScopeState.CANCELLING) {
            childScope.cancelChildren()
            future.cancel(true)
        }
        return handle
    }

    fun cancelChildren() {
        state.set(TaskScopeState.CANCELLING)
        for (child in children) {
            child.scope.cancelChildren()
            child.future.cancel(true)
        }
    }

    fun joinChildren() {
        for (child in children) {
            try {
                child.future.get()
            } catch (_: Exception) {
                // errors already handled by the child's own error propagation
            }
            child.scope.joinChildren()
        }
    }

    fun childFailed(failedScope: TaskScope, cause: Throwable) {
        if (!childFailure.compareAndSet(null, cause)) {
            val primary = childFailure.get()
            if (primary != null && primary !== cause) primary.addSuppressed(cause)
        }
        state.set(TaskScopeState.CANCELLING)
        for (child in children) {
            if (child.scope !== failedScope) {
                child.scope.cancelChildren()
                child.future.cancel(true)
            }
        }
        ownerThread?.interrupt()
    }

    companion object {
        val SCOPED_VALUE: ScopedValue<TaskScope> = ScopedValue.newInstance()

        fun current(): TaskScope? =
            if (SCOPED_VALUE.isBound) SCOPED_VALUE.get() else null
    }
}
