package bridje.antlr

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test

class ParserTest {
    @Test
    fun test() {
        val lexer = BridjeLexer(
            CharStreams.fromString(
                """
                def $ bar baz quux
                  let []
                    cock
                    
                """.trimIndent()
            )
        )

//        println(lexer.allTokens)

        val parser = BridjeParser(CommonTokenStream(lexer))

        println(parser.file().toStringTree(parser))
    }
}