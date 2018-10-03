package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    println(Context.create().eval(Source.create("brj", "((fn [x y] [y x 5 y]) 9 6)")))
}
