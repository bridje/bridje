package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskScopeBridjeTest {

    @Test
    fun `interop declarations load`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.ts.decls
              import:
                brj.runtime:
                  as(TaskScope, TS)
                  as(TaskScopeState, State)
                  as(ChildHandle, CH)
                java.util.concurrent.atomic:
                  as(AtomicReference, AR)
                java.util.concurrent:
                  as(ConcurrentLinkedQueue, CLQ)
                  as(Future, Fut)
                java.util:
                  as(ArrayList, AL)
                java.lang:
                  as(Thread, T)
                  as(Throwable, Thr)

            decl: State/ACTIVE State
            decl: State/CANCELLING State
            decl: CH/new(Fut, TS) CH
            decl: CH/.future Fut
            decl: CH/.scope TS
            decl: TS/.state AR
            decl: TS/.children CLQ
            decl: TS/.childFailure AR
            decl: TS/.ownerThread T?
            decl: [a] AR/.get() a?
            decl: [a] AR/.set(a?) Nothing
            decl: [a] AR/.compareAndSet(a?, a?) Bool
            decl: [a] CLQ/.add(a) Bool
            decl: [a] Fut/.get() a
            decl: Fut/.cancel(Bool) Bool
            decl: AL/new() AL
            decl: [a] AL/.add(a) Bool
            decl: AL/.isEmpty() Bool
            decl: [a] AL/.remove(Int) a
            decl: T/.interrupt() Nothing
            decl: Thr/.addSuppressed(Thr) Nothing

            def: result 1
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("test.ts.decls/result").asLong())
    }

    @Test
    fun `cancelChildren compiles`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.ts.cancel
              import:
                brj.runtime:
                  as(TaskScope, TS)
                  as(TaskScopeState, State)
                  as(ChildHandle, CH)
                java.util.concurrent.atomic:
                  as(AtomicReference, AR)
                java.util.concurrent:
                  as(ConcurrentLinkedQueue, CLQ)
                  as(Future, Fut)
                java.util:
                  as(ArrayList, AL)

            decl: State/CANCELLING State
            decl: CH/.future Fut
            decl: CH/.scope TS
            decl: TS/.state AR
            decl: TS/.children CLQ
            decl: [a] AR/.set(a?) Nothing
            decl: [a] CLQ/.add(a) Bool
            decl: Fut/.cancel(Bool) Bool
            decl: AL/new() AL
            decl: [a] AL/.add(a) Bool
            decl: AL/.isEmpty() Bool
            decl: [a] AL/.remove(Int) a

            def: cancelChildren(rootScope)
              let: [work AL/new()]
                AL/.add(work, rootScope)
                loop: []
                  unless: AL/.isEmpty(work)
                    let: [scope AL/.remove(work, 0)]
                      AR/.set(TS/.state(scope), State/CANCELLING)
                      doseq: [child TS/.children(scope)]
                        AL/.add(work, CH/.scope(child))
                        Fut/.cancel(CH/.future(child), true)
                      recur:

            def: result 1
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("test.ts.cancel/result").asLong())
    }

    @Test
    fun `addChild compiles`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.ts.add
              import:
                brj.runtime:
                  as(TaskScope, TS)
                  as(TaskScopeState, State)
                  as(ChildHandle, CH)
                java.util.concurrent.atomic:
                  as(AtomicReference, AR)
                java.util.concurrent:
                  as(ConcurrentLinkedQueue, CLQ)
                  as(Future, Fut)
                java.util:
                  as(ArrayList, AL)

            decl: State/CANCELLING State
            decl: CH/new(Fut, TS) CH
            decl: CH/.future Fut
            decl: CH/.scope TS
            decl: TS/.state AR
            decl: TS/.children CLQ
            decl: [a] AR/.get() a?
            decl: [a] AR/.set(a?) Nothing
            decl: [a] CLQ/.add(a) Bool
            decl: Fut/.cancel(Bool) Bool
            decl: AL/new() AL
            decl: [a] AL/.add(a) Bool
            decl: AL/.isEmpty() Bool
            decl: [a] AL/.remove(Int) a

            def: cancelChildren(rootScope)
              let: [work AL/new()]
                AL/.add(work, rootScope)
                loop: []
                  unless: AL/.isEmpty(work)
                    let: [scope AL/.remove(work, 0)]
                      AR/.set(TS/.state(scope), State/CANCELLING)
                      doseq: [child TS/.children(scope)]
                        AL/.add(work, CH/.scope(child))
                        Fut/.cancel(CH/.future(child), true)
                      recur:

            def: addChild(parent, future, childScope)
              let: [handle CH/new(future, childScope)]
                CLQ/.add(TS/.children(parent), handle)
                when: same?(AR/.get(TS/.state(parent)), State/CANCELLING)
                  cancelChildren(childScope)
                  Fut/.cancel(future, true)
                handle

            def: result 1
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("test.ts.add/result").asLong())
    }

    @Test
    fun `childFailed compiles`() = withContext { ctx ->
        ctx.evalBridje("""
            ns: test.ts.failed
              import:
                brj.runtime:
                  as(TaskScope, TS)
                  as(TaskScopeState, State)
                  as(ChildHandle, CH)
                java.util.concurrent.atomic:
                  as(AtomicReference, AR)
                java.util.concurrent:
                  as(ConcurrentLinkedQueue, CLQ)
                  as(Future, Fut)
                java.util:
                  as(ArrayList, AL)
                java.lang:
                  as(Thread, T)
                  as(Throwable, Thr)

            decl: State/CANCELLING State
            decl: CH/.future Fut
            decl: CH/.scope TS
            decl: TS/.state AR
            decl: TS/.children CLQ
            decl: TS/.childFailure AR
            decl: TS/.ownerThread T?
            decl: [a] AR/.get() a?
            decl: [a] AR/.set(a?) Nothing
            decl: [a] AR/.compareAndSet(a?, a?) Bool
            decl: [a] CLQ/.add(a) Bool
            decl: Fut/.cancel(Bool) Bool
            decl: AL/new() AL
            decl: [a] AL/.add(a) Bool
            decl: AL/.isEmpty() Bool
            decl: [a] AL/.remove(Int) a
            decl: T/.interrupt() Nothing
            decl: Thr/.addSuppressed(Thr) Nothing

            def: cancelChildren(rootScope)
              let: [work AL/new()]
                AL/.add(work, rootScope)
                loop: []
                  unless: AL/.isEmpty(work)
                    let: [scope AL/.remove(work, 0)]
                      AR/.set(TS/.state(scope), State/CANCELLING)
                      doseq: [child TS/.children(scope)]
                        AL/.add(work, CH/.scope(child))
                        Fut/.cancel(CH/.future(child), true)
                      recur:

            def: childFailed(scope, failedScope, cause)
              when: not(AR/.compareAndSet(TS/.childFailure(scope), nil, cause))
                whenLet: [primary AR/.get(TS/.childFailure(scope))]
                  unless: same?(primary, cause)
                    Thr/.addSuppressed(primary, cause)
              AR/.set(TS/.state(scope), State/CANCELLING)
              doseq: [child TS/.children(scope)]
                unless: same?(CH/.scope(child), failedScope)
                  cancelChildren(CH/.scope(child))
                  Fut/.cancel(CH/.future(child), true)
              ifLet: [t TS/.ownerThread(scope)]
                T/.interrupt(t)
                nil

            def: result 1
        """.trimIndent())
        assertEquals(1L, ctx.evalBridje("test.ts.failed/result").asLong())
    }
}
