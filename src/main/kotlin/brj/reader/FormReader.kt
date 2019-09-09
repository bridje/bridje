package brj.reader

import brj.FormBaseVisitor
import brj.FormLexer
import brj.FormParser
import brj.analyser.*
import brj.emitter.QSymbol
import brj.emitter.QSymbol.Companion.mkQSym
import brj.emitter.Symbol.Companion.mkSym
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.Reader

internal class FormReader(val source: Source) {

    private val noLoc = source.createUnavailableSection()
    private fun makeLoc(ctx: FormParser.FormContext) =
        source.createSection(ctx.start.line, ctx.start.charPositionInLine + 1, ctx.stop.line, ctx.stop.charPositionInLine + 1)

    private val concatQSymForm = QSymbolForm(mkQSym(FORM_NS, mkSym("concat")), noLoc)
    private val unquoteForm = QSymbolForm(UNQUOTE, noLoc)
    private val unquoteSplicingForm = QSymbolForm(UNQUOTE_SPLICING, noLoc)

    fun quoteForm(form: Form): Form {
        fun q(argForm: Form) = ListForm(listOf(QSymbolForm(form.qsym, noLoc), argForm), argForm.loc)

        return when (form) {
            is BooleanForm, is StringForm,
            is IntForm, is BigIntForm,
            is FloatForm, is BigFloatForm -> q(form)

            is ListForm -> q(VectorForm(form.forms.map(::quoteForm), noLoc))
            is SetForm -> q(VectorForm(form.forms.map(::quoteForm), noLoc))
            is VectorForm -> q(VectorForm(form.forms.map(::quoteForm), noLoc))
            is RecordForm -> q(VectorForm(form.forms.map(::quoteForm), noLoc))

            is SymbolForm -> q(QuotedSymbolForm(form.sym, form.loc))
            is QSymbolForm -> q(QuotedQSymbolForm(form.sym, form.loc))

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }
    }

    fun syntaxQuoteForm(form: Form, splicing: Boolean = false): Form {
        fun firstQSym(forms: List<Form>): QSymbol? = (forms[0] as? QSymbolForm)?.sym

        fun sq(argForm: Form) = ListForm(listOf(QSymbolForm(form.qsym, noLoc), argForm), argForm.loc)

        fun sqSeq(forms: List<Form>): Form {
            val nestedSplicingForm = forms.any { it is ListForm && firstQSym(it.forms) == UNQUOTE_SPLICING }
            val expandedForms = VectorForm(forms.map { syntaxQuoteForm(it, nestedSplicingForm) }, noLoc)
            return sq(if (nestedSplicingForm) ListForm(listOf(concatQSymForm, expandedForms), noLoc) else expandedForms)
        }

        var unquoteSplicing = false

        val expandedForm = when (form) {
            is BooleanForm, is StringForm,
            is IntForm, is BigIntForm,
            is FloatForm, is BigFloatForm -> sq(form)

            is ListForm -> {
                when (firstQSym(form.forms)) {
                    UNQUOTE -> form.forms[1]
                    UNQUOTE_SPLICING -> {
                        unquoteSplicing = true
                        if (splicing) {
                            form.forms[1]
                        } else {
                            throw IllegalStateException("unquote-splicing used outside of sequence")
                        }
                    }

                    else -> sqSeq(form.forms)
                }
            }

            is SymbolForm -> when (form.sym) {
                IF, LET, FN, DEF, WITH_FX -> quoteForm(form)
                else -> SyntaxQuotedSymbolForm(form.sym, form.loc)
            }

            is QSymbolForm -> SyntaxQuotedQSymbolForm(form.sym, form.loc)

            is SetForm -> sqSeq(form.forms)
            is VectorForm -> sqSeq(form.forms)
            is RecordForm -> sqSeq(form.forms)

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }

        return if (splicing && !unquoteSplicing) VectorForm(listOf(expandedForm), noLoc) else expandedForm
    }


    private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {
        override fun visitBoolean(ctx: FormParser.BooleanContext) = BooleanForm(ctx.text == "true", makeLoc(ctx))

        override fun visitString(ctx: FormParser.StringContext) = StringForm(ctx.STRING().text.removeSurrounding("\""), makeLoc(ctx))

        override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong(), makeLoc(ctx))

        override fun visitBigInt(ctx: FormParser.BigIntContext) = BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger(), makeLoc(ctx))

        override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble(), makeLoc(ctx))

        override fun visitBigFloat(ctx: FormParser.BigFloatContext) = BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal(), makeLoc(ctx))

        override fun visitSymbol(ctx: FormParser.SymbolContext) = SymbolForm(mkSym(ctx.text), makeLoc(ctx))
        override fun visitQSymbol(ctx: FormParser.QSymbolContext) = QSymbolForm(mkQSym(ctx.text), makeLoc(ctx))

        override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(::transformForm), makeLoc(ctx))
        override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(::transformForm), makeLoc(ctx))
        override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(::transformForm), makeLoc(ctx))
        override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(::transformForm), makeLoc(ctx))

        override fun visitQuote(ctx: FormParser.QuoteContext) = quoteForm(transformForm(ctx.form()))
        override fun visitSyntaxQuote(ctx: FormParser.SyntaxQuoteContext) = syntaxQuoteForm(transformForm(ctx.form()))
        override fun visitUnquote(ctx: FormParser.UnquoteContext) = ListForm(listOf(unquoteForm, transformForm(ctx.form())), makeLoc(ctx))
        override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = ListForm(listOf(unquoteSplicingForm, transformForm(ctx.form())), makeLoc(ctx))
    })

    fun readForms(reader: Reader): List<Form> =
        FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(reader))))
            .file().form()
            .toList()
            .map(::transformForm)

    companion object {
        internal fun readSourceForms(source: Source) =
            FormReader(source).readForms(source.reader)
    }
}
