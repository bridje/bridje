import brj.BridjeMain
import brj.RunCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test

fun main() {
    val command = BridjeMain().subcommands(RunCommand())
    val result = command.test("run main_test:simple")

    println("Exit code: ${result.statusCode}")
    println("Output:")
    println(result.output)
}
