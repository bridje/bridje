package brj

import bridje.antlr.BridjeBaseVisitor
import bridje.antlr.BridjeLexer
import bridje.antlr.BridjeParser
import bridje.antlr.BridjeParser.*
import brj.runtime.QSymbol.Companion.qsym
import brj.runtime.Symbol.Companion.sym
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun readForms(source: Source): List<Form> {
    val lexer = BridjeLexer(CharStreams.fromReader(source.reader))
    val parser = BridjeParser(CommonTokenStream(lexer))

    return parser.file().form().map { formCtx ->
        formCtx.accept(object : BridjeBaseVisitor<Form>() {
            private val FormContext.loc: SourceSection
                get() {
                    val startIdx = start.startIndex
                    val stopIdx = stop.stopIndex
                    return source.createSection(startIdx, stopIdx - startIdx)
                }

            override fun visitNil(ctx: NilContext) = NilForm(ctx.loc)

            override fun visitBool(ctx: BoolContext) = BoolForm(ctx.text.toBoolean(), ctx.loc)

            override fun visitInt(ctx: IntContext) = IntForm(ctx.text.toInt(), ctx.loc)

            override fun visitFloat(ctx: FloatContext) = TODO()
            override fun visitBigDecimal(ctx: BigDecimalContext) = TODO()
            override fun visitBigInteger(ctx: BigIntegerContext) = TODO()

            override fun visitString(ctx: StringContext) =
                StringForm(ctx.text.removeSurrounding("\""), ctx.loc)

            override fun visitSymbol(ctx: SymbolContext) = SymbolForm(ctx.text.sym, ctx.loc)

            override fun visitDotSymbol(ctx: DotSymbolContext) =
                DotSymbolForm(ctx.text.removePrefix(".").sym, ctx.loc)

            override fun visitSymbolDot(ctx: SymbolDotContext) =
                SymbolDotForm(ctx.text.removeSuffix(".").sym, ctx.loc)

            override fun visitQSymbol(ctx: QSymbolContext) = QSymbolForm(ctx.text.qsym, ctx.loc)

            override fun visitQSymbolDot(ctx: QSymbolDotContext) =
                QSymbolDotForm(ctx.text.removeSuffix(".").qsym, ctx.loc)

            override fun visitDotQSymbol(ctx: DotQSymbolContext): DotQSymbolForm {
                val (nsStr, localStr) = ctx.text.split("/.")
                return DotQSymbolForm(Pair(nsStr.sym, localStr.sym).qsym, ctx.loc)
            }

            override fun visitKeyword(ctx: KeywordContext) =
                KeywordForm(ctx.text.removePrefix(":").sym, ctx.loc)

            override fun visitKeywordDot(ctx: KeywordDotContext) =
                KeywordDotForm(ctx.text.removeSurrounding(":", ".").sym, ctx.loc)

            override fun visitQKeyword(ctx: QKeywordContext) =
                QKeywordForm(ctx.text.removePrefix(":").qsym, ctx.loc)

            override fun visitQKeywordDot(ctx: QKeywordDotContext) =
                QKeywordDotForm(ctx.text.removeSurrounding(":", ".").qsym, ctx.loc)

            private val formVisitor = this
            private fun List<FormContext>.toForms() = map { it.accept(formVisitor) }

            override fun visitList(ctx: ListContext) = ListForm(ctx.form().toForms(), ctx.loc)
            override fun visitVector(ctx: VectorContext) = VectorForm(ctx.form().toForms(), ctx.loc)
            override fun visitSet(ctx: SetContext) = SetForm(ctx.form().toForms(), ctx.loc)
            override fun visitRecord(ctx: RecordContext) = RecordForm(ctx.form().toForms(), ctx.loc)

            override fun visitQuote(ctx: QuoteContext) = TODO()
            override fun visitUnquote(ctx: UnquoteContext) = TODO()
            override fun visitUnquoteSplicing(ctx: UnquoteSplicingContext) = TODO()
        })
    }
}