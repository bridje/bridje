package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main() {
    val ctx = Context.newBuilder("brj").allowAllAccess(true).build()

    ctx.enter()

    val foo = Symbol.mkSym("foo")
    val bar = Symbol.mkSym("bar")

    val fooSource = Source.create("brj", """
        (ns foo
          {:aliases {b bar, forms brj.forms}
           :refers {bar #{baz}}
           :imports {Foo (java brj.Foo
                               (:: (isZero Int) Bool)
                               (:: (dec Int) Int)
                               (:: (conj #{a} a) #{a})
                               (:: (plus Int Int) Int))}})

        (:: :Void)
        (:: Void (+ :Void))

        (:: (:Just a) a)
        (:: :Nothing)

        ;; (:: foo [(+ (:Just Int) :Nothing)])
        (def foo [:Nothing (:Just 4)])

        (def bar
          (case (:Just 4)
            (:Just x) x
            :Nothing 0))

        (def just (:Just 4))

        (def x
          (let [quux 10N]
            [quux baz]))

        (:: (! (println! Str)) Void)
        ;;   (def (println! s) :Void)

        ;;   (:: (! (read-line!)) Str))

        (:: :first-name Str)
        (:: :last-name Str)
        ;; (:: User {:first-name :last-name})

        (def (count-down x)
          (loop [y x
                 res #{}]
            (if (Foo/isZero y) res (recur (Foo/dec y) (Foo/conj res y)))))

        (def counted-down (count-down 5))

        (:: (my-fn a a) [a])
        (def (my-fn x y)
          [y x])

        (defmacro (if-not pred then else)
          (:forms/ListForm ['if else then]))
        """.trimIndent())

    val barSource = Source.create("brj", """
        (ns bar {})

        (:: baz BigInt)
        (def baz 42N)
        (def a-set #{45N 90N})
    """.trimIndent())

    require(setOf(foo), mapOf(foo to fooSource, bar to barSource))

    val value = ctx.eval(Source.create("brj", "{:foo/first-name \"James\"}"))

    println("value: $value")

    println(ctx.eval(Source.create("brj", "foo/count-down")).execute(6))

    ctx.leave()
}
