package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    val ctx = Context.create()

    require(ctx, Source.create("brj", "(ns foo) (def x 10N)"))

//    println(ctx.eval(Source.create("brj", "foo/x")))
}
