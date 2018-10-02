package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    println(Context.create().eval(Source.create("brj", "((fn pair [x y] [x y]) 5 3)")))
}
