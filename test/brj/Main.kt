package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    val ctx = Context.newBuilder("brj").allowAllAccess(true).build()

    ctx.enter()

    val foo = Symbol.intern("foo")
    val bar = Symbol.intern("bar")

    val fooSource = Source.create("brj", """
        (ns foo
          {aliases {b bar}
           refers {bar #{baz}}
           java {brj.Foo #{(:: (isZero Int) Bool)}}})

        (defdata (Maybe a) (Just a) Nothing)

        ;(:: foo [(foo/Maybe Int)])
        (def foo [Nothing (Just 4)])

        (def bar
          (case (Just 4)
            (Just x) x
            Nothing 0))

        (def just (Just 4))

        (def x
          (let [quux 10N]
            [quux baz]))

        (def (zero? x) (Foo/isZero x))

        (:: (my-fn a a) [a])
        (def (my-fn x y)
          [y x])
        """.trimIndent())

    val barSource = Source.create("brj", """
        (ns bar {})

        (:: baz BigInt)
        (def baz 42N)
        (def a-set #{45N 90N})
    """.trimIndent())

    require(setOf(foo), mapOf(foo to fooSource, bar to barSource))

    val value = ctx.eval(Source.create("brj", "(foo/zero? 0)"))

    println("value: $value")

    ctx.leave()
}
