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

    val buffer = StringBuilder()

    generateSequence(::readLine).forEach { line ->
        // Null byte signals end of input from Conjure
        if (line.contains('\u0000')) {
            val code = buffer.toString().trim()
            buffer.clear()

            if (code.isNotEmpty()) {
                try {
                    val source = Source.newBuilder("bridje", code, "<repl>").build()
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
        } else if (buffer.isEmpty() && line.isBlank()) {
            // Empty line at top level, just show prompt again
            print(PROMPT)
            System.out.flush()
        } else {
            // Accumulate lines
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append(line)
        }
    }

    ctx.close()
}
