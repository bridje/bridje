package brj

import org.junit.jupiter.api.Test

internal class ReaderTest {
    @Test
    internal fun `test quoting`() {
        println(readForms("'[true foo]"))
    }
}
