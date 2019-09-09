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
        source.createSection(ctx.start.line, ctx.start.charPositionInLine, ctx.stop.line, ctx.stop.charPositionInLine)

    private val concatQSymForm = QSymbolForm(noLoc, mkQSym(FORM_NS, mkSym("concat")))
    private val unquoteForm = QSymbolForm(noLoc, UNQUOTE)
    private val unquoteSplicingForm = QSymbolForm(noLoc, UNQUOTE_SPLICING)

    fun quoteForm(form: Form): Form {
        fun q(argForm: Form) = ListForm(argForm.loc, listOf(QSymbolForm(noLoc, form.qsym), argForm))

        return when (form) {
            is BooleanForm, is StringForm,
            is IntForm, is BigIntForm,
            is FloatForm, is BigFloatForm -> q(form)

            is ListForm -> q(VectorForm(noLoc, form.forms.map(::quoteForm)))
            is SetForm -> q(VectorForm(noLoc, form.forms.map(::quoteForm)))
            is VectorForm -> q(VectorForm(noLoc, form.forms.map(::quoteForm)))
            is RecordForm -> q(VectorForm(noLoc, form.forms.map(::quoteForm)))

            is SymbolForm -> q(QuotedSymbolForm(form.loc, form.sym))
            is QSymbolForm -> q(QuotedQSymbolForm(form.loc, form.sym))

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }
    }

    fun syntaxQuoteForm(form: Form, splicing: Boolean = false): Form {
        fun firstQSym(forms: List<Form>): QSymbol? = (forms[0] as? QSymbolForm)?.sym

        fun sq(argForm: Form) = ListForm(argForm.loc, listOf(QSymbolForm(noLoc, form.qsym), argForm))

        fun sqSeq(forms: List<Form>): Form {
            val nestedSplicingForm = forms.any { it is ListForm && firstQSym(it.forms) == UNQUOTE_SPLICING }
            val expandedForms = VectorForm(noLoc, forms.map { syntaxQuoteForm(it, nestedSplicingForm) })
            return sq(if (nestedSplicingForm) ListForm(noLoc, listOf(concatQSymForm, expandedForms)) else expandedForms)
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
                else -> SyntaxQuotedSymbolForm(form.loc, form.sym)
            }

            is QSymbolForm -> SyntaxQuotedQSymbolForm(form.loc, form.sym)

            is SetForm -> sqSeq(form.forms)
            is VectorForm -> sqSeq(form.forms)
            is RecordForm -> sqSeq(form.forms)

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }

        return if (splicing && !unquoteSplicing) VectorForm(noLoc, listOf(expandedForm)) else expandedForm
    }


    private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {
        override fun visitBoolean(ctx: FormParser.BooleanContext) = BooleanForm(makeLoc(ctx), ctx.text == "true")

        override fun visitString(ctx: FormParser.StringContext) = StringForm(makeLoc(ctx), ctx.STRING().text.removeSurrounding("\""))

        override fun visitInt(ctx: FormParser.IntContext) = IntForm(makeLoc(ctx), ctx.INT().text.toLong())

        override fun visitBigInt(ctx: FormParser.BigIntContext) = BigIntForm(makeLoc(ctx), ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

        override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(makeLoc(ctx), ctx.FLOAT().text.toDouble())

        override fun visitBigFloat(ctx: FormParser.BigFloatContext) = BigFloatForm(makeLoc(ctx), ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

        override fun visitSymbol(ctx: FormParser.SymbolContext) = SymbolForm(makeLoc(ctx), mkSym(ctx.text))
        override fun visitQSymbol(ctx: FormParser.QSymbolContext) = QSymbolForm(makeLoc(ctx), mkQSym(ctx.text))

        override fun visitList(ctx: FormParser.ListContext) = ListForm(makeLoc(ctx), ctx.form().map(::transformForm))
        override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(makeLoc(ctx), ctx.form().map(::transformForm))
        override fun visitSet(ctx: FormParser.SetContext) = SetForm(makeLoc(ctx), ctx.form().map(::transformForm))
        override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(makeLoc(ctx), ctx.form().map(::transformForm))

        override fun visitQuote(ctx: FormParser.QuoteContext) = quoteForm(transformForm(ctx.form()))
        override fun visitSyntaxQuote(ctx: FormParser.SyntaxQuoteContext) = syntaxQuoteForm(transformForm(ctx.form()))
        override fun visitUnquote(ctx: FormParser.UnquoteContext) = ListForm(makeLoc(ctx), listOf(unquoteForm, transformForm(ctx.form())))
        override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = ListForm(makeLoc(ctx), listOf(unquoteSplicingForm, transformForm(ctx.form())))
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
