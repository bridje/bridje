package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    val ctx = Context.create()

    ctx.enter()

    require(ctx, Source.create("brj", "(ns foo) (def x [10N 45N]) (def (bar x y) [y x])"))
    println("value: ${ctx.eval(Source.create("brj", "foo/x"))}")

    ctx.leave()
}
