package brj.analyser

import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol.Companion.mkSym
import brj.readForms
import brj.runtime.NSEnv
import brj.runtime.VariantKey
import brj.runtime.VariantKeyVar
import brj.types.MonoType
import brj.types.RowKey
import brj.types.VariantType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TypeAnalyserTest {
    private val boolKey = VariantKey(mkQSym(":user/BooleanForm"), emptyList(), emptyList())
    private val strKey = VariantKey(mkQSym(":user/StringForm"), emptyList(), emptyList())

    private val dummyResolver = Resolver.NSResolver(
        nsEnv = NSEnv(
            ns = mkSym("user"),
            vars = mapOf(
                boolKey.sym.base to VariantKeyVar(boolKey, 42),
                strKey.sym.base to VariantKeyVar(strKey, 52))))

    private fun analyseMonoType(s: String): MonoType =
        TypeAnalyser(dummyResolver).monoTypeAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses variant type declaration`() {
        val s = "(+ :BooleanForm :StringForm)"

        assertEquals(
            mapOf(boolKey to RowKey(emptyList()), strKey to RowKey(emptyList())),
            (analyseMonoType(s) as VariantType).possibleKeys)

    }
}