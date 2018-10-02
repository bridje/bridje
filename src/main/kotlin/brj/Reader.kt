package brj

import brj.Form.*
import brj.grammar.FormBaseVisitor
import brj.grammar.FormLexer
import brj.grammar.FormParser
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

object Reader {
    private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

        override fun visitBoolean(ctx: FormParser.BooleanContext): Form = BooleanForm(ctx.text == "true")

        override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

        override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong())

        override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
            BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

        override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())

        override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
            BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

        override fun visitSymbol(ctx: FormParser.SymbolContext): Form = SymbolForm(Symbol(ctx.text))

        override fun visitNamespacedSymbol(ctx: FormParser.NamespacedSymbolContext) =
            Regex("(.+)/(.+)").matchEntire(ctx.text)!!
                .groups
                .let { groups -> NamespacedSymbolForm(NamespacedSymbol(Symbol(groups[1]!!.value), groups[2]!!.value)) }

        override fun visitKeyword(ctx: FormParser.KeywordContext) = KeywordForm(Keyword(ctx.text.removePrefix(":")))


        override fun visitNamespacedKeyword(ctx: FormParser.NamespacedKeywordContext): Form =
            Regex(":(.+)/(.+)").matchEntire(ctx.text)!!
                .groups
                .let { groups -> NamespacedKeywordForm(NamespacedKeyword(Symbol(groups[1]!!.value), groups[2]!!.value)) }

        override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(::transformForm))
        override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(::transformForm))
        override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(::transformForm))
        override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(::transformForm))

        override fun visitQuote(ctx: FormParser.QuoteContext) = QuoteForm(transformForm(ctx.form()))
        override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = UnquoteSplicingForm(transformForm(ctx.form()))
        override fun visitUnquote(ctx: FormParser.UnquoteContext): Form = UnquoteForm(transformForm(ctx.form()))
    })

    fun readForms(source: Source): List<Form> =
        FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(source.reader))))
            .file().form()
            .toList()
            .map(::transformForm)
}
