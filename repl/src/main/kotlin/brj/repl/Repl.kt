package brj.repl

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source

private const val PROMPT = "bridje=> "

fun main() {
    val ctx =
        Context
            .newBuilder("bridje")
            .allowAllAccess(true)
            .build()

    print(PROMPT)
    System.out.flush()

    generateSequence(::readLine).forEach { line ->
        if (line.isNotBlank()) {
            try {
                val source = Source.newBuilder("bridje", line, "<repl>").build()
                val result = ctx.eval(source)
                println(result)
            } catch (e: PolyglotException) {
                System.err.println("Error: ${e.message}")
            } catch (e: Exception) {
                System.err.println("Error: ${e.message}")
            }
        }

        print(PROMPT)
        System.out.flush()
    }

    ctx.close()
}
