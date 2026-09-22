package brj.types

import brj.BridjeLanguage
import brj.Reader.Companion.readForms
import brj.analyser.*
import brj.runtime.*
import brj.evalBridje
import brj.withContext
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Runs the new checker over the stdlib's own definitions, one at a time, without wiring it into the
// runtime. Each definition gets a time budget, so a pathological solve names the definition and fails
// this test instead of hanging the suite. Type errors are findings on the way to parity: the set
// below is what fails today, and the test holds it fixed so a change can only shrink it knowingly.
class StdlibShadowTest {

    private val budgetMillis = 2_000L

    // core loads with the language; the rest load on demand, so the test loads them itself.
    private val namespaces = listOf("brj.core", "brj.test", "brj.concurrent", "brj.fs", "brj.time", "brj.str", "brj.bytes")

    // Definitions the checker rejects today, keyed "ns/name", and why in a word. Empty means parity.
    private val expectedFailures: Map<String, String> = mapOf()

    private sealed interface Outcome {
        data class Ok(val shown: String, val millis: Long) : Outcome
        data class Failed(val message: String, val millis: Long) : Outcome
        data object TimedOut : Outcome
    }

    private fun resource(ns: String): URL =
        BridjeLanguage::class.java.classLoader.getResource(ns.replace('.', '/') + ".brj") ?: fail("no source for $ns")

    @Test
    fun `every stdlib definition infers within budget`() = withContext { polyglot ->
        // The language, and with it core, initialises on first use.
        polyglot.evalBridje("nil")
        for (ns in namespaces.drop(1)) polyglot.eval(org.graalvm.polyglot.Source.newBuilder("bridje", resource(ns)).build())

        val ctx = BridjeContext.current()
        // Globals typed so far, in definition order, so later definitions see this checker's schemes.
        val schemes = HashMap<brj.GlobalVar, Scheme>()
        val typeCtx = typeCtx(ctx) { schemes[it] }

        val outcomes = LinkedHashMap<String, Outcome>()
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "shadow-typing").apply { isDaemon = true } }
        try {
            ns@ for (ns in namespaces) {
                val nsEnv = ctx.namespaces[ns.sym] ?: fail("namespace $ns did not load")
                val (_, forms) = Source.newBuilder("bridje", resource(ns)).mimeType("text/brj").build().readForms().toList().analyseNs()
                val declared = HashMap<Symbol, Type>()
                for (form in forms) {
                    val expr = Analyser(ctx, nsEnv).analyse(form)
                    val (name, value) = when (expr) {
                        is DeclExpr -> { declared[expr.name] = expr.declaredType; continue }
                        is DefExpr -> expr.name.name to expr.valueExpr
                        is DefMacroExpr -> expr.name.name to expr.fn
                        else -> continue
                    }
                    val gv = nsEnv.vars[name.sym] ?: fail("$ns/$name not defined at runtime")
                    val decl = if (expr is DefMacroExpr) macroType(expr.fn) else declared.remove(name.sym)
                    val start = System.nanoTime()
                    val future = executor.submit<Pair<Scheme, String>> {
                        val inferred = value.typing(typeCtx).generalise()
                        val shown = inferred.simplify(typeCtx).toString()
                        val scheme = decl?.let { checkDeclared(inferred, it, typeCtx) } ?: inferred
                        scheme to (if (decl != null) "$shown  ⊑ decl" else shown)
                    }
                    val outcome = try {
                        val (scheme, shown) = future.get(budgetMillis, TimeUnit.MILLISECONDS)
                        schemes[gv] = scheme
                        Outcome.Ok(shown, elapsed(start))
                    } catch (_: TimeoutException) {
                        future.cancel(true)
                        Outcome.TimedOut
                    } catch (e: java.util.concurrent.ExecutionException) {
                        Outcome.Failed(e.cause?.message ?: e.toString(), elapsed(start))
                    }
                    outcomes["$ns/$name"] = outcome
                    if (outcome is Outcome.TimedOut) break@ns
                }
            }
        } finally {
            executor.shutdownNow()
        }

        val report = outcomes.entries.joinToString("\n") { (name, o) ->
            when (o) {
                is Outcome.Ok -> "  ok      %5dms  %s : %s".format(o.millis, name, o.shown)
                is Outcome.Failed -> "  FAILED  %5dms  %s : %s".format(o.millis, name, o.message.lines().first())
                Outcome.TimedOut -> "  TIMEOUT        %s".format(name)
            }
        }
        println("types over the stdlib:\n$report")

        val timedOut = outcomes.filterValues { it is Outcome.TimedOut }.keys
        assertTrue(timedOut.isEmpty(), "definitions exceeding ${budgetMillis}ms: $timedOut")

        val failed = outcomes.filterValues { it is Outcome.Failed }.keys
        assertEquals(expectedFailures.keys, failed, "definitions the checker rejects changed:\n$report")
    }

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000
}
