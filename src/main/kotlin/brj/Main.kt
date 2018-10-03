package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    println(Context.create().eval(Source.create("brj", "(let [x 5, y [x 3]] [y [x 5] y])")))
}
