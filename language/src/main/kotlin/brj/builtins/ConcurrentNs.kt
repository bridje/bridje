package brj.builtins

import brj.BridjeLanguage
import brj.GlobalVar
import brj.NsEnv
import brj.analyser.NsDecl
import brj.runtime.BridjeFunction
import brj.types.*

object ConcurrentNs {
    fun create(language: BridjeLanguage): NsEnv {
        val t = freshType()
        val spawnType = FnType(
            listOf(FnType(emptyList(), t).notNull()),
            FutureType(t).notNull()
        ).notNull()

        val u = freshType()
        val awaitType = FnType(
            listOf(FutureType(u).notNull()),
            u
        ).notNull()

        val interruptType = FnType(
            listOf(FutureType(freshType()).notNull()),
            BoolType.notNull()
        ).notNull()

        val ensureActiveType = FnType(emptyList(), nullType()).notNull()

        val sleepMsType = FnType(listOf(IntType.notNull()), nullType()).notNull()

        return NsEnv(
            vars = mapOf(
                "spawn" to GlobalVar("spawn", BridjeFunction(SpawnNode(language).callTarget), type = spawnType),
                "await" to GlobalVar("await", BridjeFunction(AwaitNode(language).callTarget), type = awaitType),
                "interrupt" to GlobalVar("interrupt", BridjeFunction(InterruptNode(language).callTarget), type = interruptType),
                "ensureActive" to GlobalVar("ensureActive", BridjeFunction(EnsureActiveNode(language).callTarget), type = ensureActiveType),
                "sleepMs" to GlobalVar("sleepMs", BridjeFunction(SleepMsNode(language).callTarget), type = sleepMsType),
            ),
            nsDecl = NsDecl("brj.concurrent"),
        )
    }
}
