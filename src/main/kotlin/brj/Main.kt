package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    println(Context.create().eval(Source.create("brj", "#{1 2 3}")).asHostObject<Any>())
}
