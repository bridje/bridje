package brj

import brj.grammar.FormBaseVisitor
import brj.grammar.FormLexer
import brj.grammar.FormParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger

sealed class Form {

    data class BooleanForm(val bool: Boolean) : Form()
    data class StringForm(val string: String) : Form()
    data class IntForm(val int: Long) : Form()
    data class BigIntForm(val bigInt: BigInteger) : Form()
    data class FloatForm(val float: Double) : Form()
    data class BigFloatForm(val bigFloat: BigDecimal) : Form()

    sealed class ASymbolForm(open val sym: ASymbol) : Form() {
        data class SymbolForm(override val sym: ASymbol.Symbol) : ASymbolForm(sym)
        data class KeywordForm(override val sym: ASymbol.Keyword) : ASymbolForm(sym)
    }

    sealed class AQSymbolForm(open val sym: AQSymbol) : Form() {
        data class QSymbolForm(override val sym: AQSymbol.QSymbol) : AQSymbolForm(sym)
        data class QKeywordForm(override val sym: AQSymbol.QKeyword) : AQSymbolForm(sym)
    }

    data class ListForm(val forms: List<Form>) : Form()
    data class VectorForm(val forms: List<Form>) : Form()
    data class SetForm(val forms: List<Form>) : Form()
    data class RecordForm(val forms: List<Form>) : Form()
    data class QuoteForm(val form: Form) : Form()
    data class UnquoteForm(val form: Form) : Form()
    data class UnquoteSplicingForm(val form: Form) : Form()

    companion object {

        private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

            override fun visitBoolean(ctx: FormParser.BooleanContext): Form = BooleanForm(ctx.text == "true")

            override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

            override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong())

            override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
                BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

            override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())

            override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
                BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

            override fun visitSymbol(ctx: FormParser.SymbolContext): Form = ASymbolForm.SymbolForm(ASymbol.Symbol.intern(ctx.text))

            override fun visitNamespacedSymbol(ctx: FormParser.NamespacedSymbolContext) =
                Regex("(.+)/(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> AQSymbolForm.QSymbolForm(AQSymbol.QSymbol.intern(ASymbol.Symbol.intern(groups[1]!!.value), ASymbol.Symbol.intern(groups[2]!!.value))) }

            override fun visitKeyword(ctx: FormParser.KeywordContext) = ASymbolForm.KeywordForm(ASymbol.Keyword.intern(ctx.text.removePrefix(":")))


            override fun visitNamespacedKeyword(ctx: FormParser.NamespacedKeywordContext): Form =
                Regex(":(.+)/(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> AQSymbolForm.QKeywordForm(AQSymbol.QKeyword.intern(ASymbol.Symbol.intern(groups[1]!!.value), ASymbol.Symbol.intern(groups[2]!!.value))) }

            override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(::transformForm))
            override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(::transformForm))
            override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(::transformForm))
            override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(::transformForm))

            override fun visitQuote(ctx: FormParser.QuoteContext) = QuoteForm(transformForm(ctx.form()))
            override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = UnquoteSplicingForm(transformForm(ctx.form()))
            override fun visitUnquote(ctx: FormParser.UnquoteContext): Form = UnquoteForm(transformForm(ctx.form()))
        })

        fun readForms(reader: Reader): List<Form> =
            FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(reader))))
                .file().form()
                .toList()
                .map(::transformForm)

        fun readForms(s: String): List<Form> = readForms(StringReader(s))
    }


}