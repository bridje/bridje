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

