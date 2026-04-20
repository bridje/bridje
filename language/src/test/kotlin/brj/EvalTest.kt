package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

internal inline fun <R> withContext(f: (Context) -> R): R =
    Context.newBuilder()
        .allowAllAccess(true)
        .logHandler(System.err)
        .build()
        .use { ctx ->
            try {
                ctx.enter()
                f(ctx)
            } finally {
                ctx.leave()
            }
        }

internal fun Context.evalBridje(src: String) = eval("bridje", src)

private const val FORMS_NS_HEADER =
    "ns: test.forms\n" +
    "  require:\n" +
    "    brj: rdr\n"

/**
 * Runs `expr` inside an anonymous ns that requires `brj.rdr`.
 * Wraps `expr` in `def: __result` so the returned [Value] is the expression's value,
 * not the ns itself.
 */
internal fun Context.evalBridjeForms(expr: String): Value {
    val body = expr.trimIndent().prependIndent("  ")
    val ns = evalBridje(FORMS_NS_HEADER + "def: __result\n" + body)
    return ns.getMember("__result")
}
