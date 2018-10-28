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
          (let [quux 10N]
            [quux baz]))

        (:: (my-fn a a) [a])
        (def (my-fn x y)
          [y x])
        """.trimIndent())

    val barSource = Source.create("brj", """
        (ns bar)

        (def baz 42N)
    """.trimIndent())

    require(setOf(foo), mapOf(foo to fooSource, bar to barSource))

    println("value: ${ctx.eval(Source.create("brj", "(foo/my-fn foo/x [bar/baz 60N 99N])"))}")

    ctx.leave()
}
