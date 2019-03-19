package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TypeAnalyserTest {
    val dummyVar = object : Any() {}

    private val boolKey = VariantKey(mkQSym(":user/BooleanForm"), emptyList(), emptyList())
    private val strKey = VariantKey(mkQSym(":user/StringForm"), emptyList(), emptyList())

    private fun analyseMonoType(s: String): MonoType =
        TypeAnalyser(Env(), NSEnv(mkSym("user"),
            vars = mapOf(
                boolKey.sym.base to VariantKeyVar(boolKey, dummyVar),
                strKey.sym.base to VariantKeyVar(strKey, dummyVar))))
            .monoTypeAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses variant type declaration`() {
        val s = "(+ :BooleanForm :StringForm)"

        assertEquals(
            mapOf(
                boolKey to KeyType(boolKey, emptyList()),
                strKey to KeyType(strKey, emptyList())),
            (analyseMonoType(s) as VariantType).keyTypes)

    }
}