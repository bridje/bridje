package brj.analyser

import brj.reader.readForms
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol.Companion.mkSym
import brj.types.FnType
import brj.types.IntType
import brj.types.StringType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NSHeaderTest {
    private fun parseNSHeader(s: String) = NSHeader.nsHeaderParser(ParserState(readForms(s)))

    @Test
    internal fun testNSHeader() {
        assertEquals(NSHeader(ns = mkSym("foo")),
            parseNSHeader("(ns foo)"))

        assertEquals(mapOf(
            mkSym("baz") to mkQSym("bar/baz"),
            mkSym("Baz") to mkQSym("bar/Baz"),
            mkSym(":baz") to mkQSym(":bar/baz"),
            mkSym(":Baz") to mkQSym(":bar/Baz")),

            parseNSHeader("(ns foo {:refers {bar #{:baz :Baz baz Baz}}})")!!.refers)

        assertEquals(mapOf(
            mkSym("bar") to BridjeAlias(mkSym("foo.bar")),
            mkSym("Str") to
                JavaAlias(mkSym("foo\$Str"), String::class, mapOf(
                    mkSym("bar") to
                        JavaInteropDecl(
                            mkSym("bar"),
                            FnType(listOf(StringType), IntType))))),

            parseNSHeader("""
              (ns foo
                {:aliases {bar foo.bar
                           Str (java java.lang.String
                                      (:: (bar Str) Int))}})"""
                .trimIndent())!!.aliases)
    }
}