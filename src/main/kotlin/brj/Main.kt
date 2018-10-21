package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    val ctx = Context.create()

    ctx.enter()

    val foo = Symbol.create("foo")
    val bar = Symbol.create("bar")

    val fooSource = Source.create("brj", """
        (ns foo
          {:aliases {b bar},
           :refers {bar #{baz}}})

        (def x
          [10N baz])

        (def (my-fn x y)
          [y x])
        """.trimIndent())

    val barSource = Source.create("brj", """
        (ns bar)
        (def baz 42)
    """.trimIndent())

    require(ctx, foo, mapOf(foo to fooSource, bar to barSource))

    println("value: ${ctx.eval(Source.create("brj", "foo/x"))}")

    ctx.leave()
}
