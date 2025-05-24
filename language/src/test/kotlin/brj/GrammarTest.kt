package brj

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrammarTest {
    @Test
    fun `loads grammar`() {
        val parser = TreeSitterBridje.parser()
        assertEquals(
            "(source_file (list (symbol_dot) (string)))",
            (parser.parse("""(Instant. "foo")""").get().rootNode.toSexp())
        )
    }
}