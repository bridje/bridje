package brj.reader

import brj.analyser.*
import brj.reader.FormParser.*
import brj.runtime.QSymbol
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.Reader

internal class FormReader(private val source: Source) {

    private fun makeLoc(ctx: FormContext) =
        source.createSection(ctx.start.line, ctx.start.charPositionInLine + 1, ctx.text.length)

    private val concatSymForm = QSymbolForm(QSymbol(CORE_NS, Symbol(ID, "concat")))
    private val unquoteForm = QSymbolForm(UNQUOTE)
    private val unquoteSplicingForm = QSymbolForm(UNQUOTE_SPLICING)

    fun quoteForm(form: Form): Form {
        fun q(argForm: Form) = ListForm(listOf(QSymbolForm(form.variantKey.sym), argForm)).withLoc(form.loc)

        return when (form) {
            is BooleanForm, is StringForm,
            is IntForm, is BigIntForm,
            is FloatForm, is BigFloatForm -> q(form)

            is ListForm -> q(VectorForm(form.forms.map(::quoteForm)))
            is SetForm -> q(VectorForm(form.forms.map(::quoteForm)))
            is VectorForm -> q(VectorForm(form.forms.map(::quoteForm)))
            is RecordForm -> q(VectorForm(form.forms.map(::quoteForm)))

            is SymbolForm -> q(QuotedSymbolForm(form.sym))
            is QSymbolForm -> q(QuotedQSymbolForm(form.sym))

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }
    }

    fun syntaxQuoteForm(form: Form, splicing: Boolean = false): Form {
        fun firstQSym(forms: List<Form>): QSymbol? = (forms[0] as? QSymbolForm)?.sym

        fun sq(argForm: Form) = ListForm(listOf(QSymbolForm(form.variantKey.sym), argForm)).withLoc(argForm.loc)

        fun sqSeq(forms: List<Form>): Form {
            val nestedSplicingForm = forms.any { it is ListForm && firstQSym(it.forms) == UNQUOTE_SPLICING }
            val expandedForms = VectorForm(forms.map { syntaxQuoteForm(it, nestedSplicingForm) })
            return sq(if (nestedSplicingForm) ListForm(listOf(concatSymForm, expandedForms)) else expandedForms)
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
                else -> SyntaxQuotedSymbolForm(form.sym)
            }

            is QSymbolForm -> SyntaxQuotedQSymbolForm(form.sym)

            is SetForm -> sqSeq(form.forms)
            is VectorForm -> sqSeq(form.forms)
            is RecordForm -> sqSeq(form.forms)

            // TODO we should support QuotedSymbolForm/QuotedQSymbolForm here for nested quotes
            else -> throw UnsupportedOperationException()
        }

        return if (splicing && !unquoteSplicing) VectorForm(listOf(expandedForm)) else expandedForm
    }

    private object NSSymVisitor: FormBaseVisitor<Symbol>() {
        override fun visitNsIdSym(ctx: NsIdSymContext) = Symbol(ID, ctx.LOWER_SYM().text)
        override fun visitNsTypeSym(ctx: NsTypeSymContext) = Symbol(TYPE, ctx.UPPER_SYM().text)
    }

    private object SymVisitor: FormBaseVisitor<Symbol>() {
        override fun visitIdSym(ctx: IdSymContext) = Symbol(ID, ctx.text)
        override fun visitTypeSym(ctx: TypeSymContext) = Symbol(TYPE, ctx.UPPER_SYM().text)
        override fun visitRecordSym(ctx: RecordSymContext) = Symbol(RECORD, ctx.LOWER_SYM().text)
        override fun visitVariantSym(ctx: VariantSymContext) = Symbol(VARIANT, ctx.UPPER_SYM().text)
    }

    private object QSymVisitor: FormBaseVisitor<QSymbol>() {
        private fun mkQSym(nsSymCtx: NsSymContext, local: Symbol) = QSymbol(nsSymCtx.accept(NSSymVisitor), local)

        override fun visitQIdSym(ctx: QIdSymContext) = mkQSym(ctx.nsSym(), Symbol(ID, ctx.LOWER_SYM().text))
        override fun visitQTypeSym(ctx: QTypeSymContext) = mkQSym(ctx.nsSym(), Symbol(TYPE, ctx.UPPER_SYM().text))
        override fun visitQRecordSym(ctx: QRecordSymContext) = mkQSym(ctx.nsSym(), Symbol(RECORD, ctx.LOWER_SYM().text))
        override fun visitQVariantSym(ctx: QVariantSymContext) = mkQSym(ctx.nsSym(), Symbol(VARIANT, ctx.UPPER_SYM().text))
    }

    private fun transformForm(formContext: FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {
        override fun visitBoolean(ctx: BooleanContext) = BooleanForm(ctx.text == "true")
        override fun visitString(ctx: StringContext) = StringForm(ctx.STRING().text.removeSurrounding("\""))

        override fun visitInt(ctx: IntContext) = IntForm(ctx.INT().text.toLong())
        override fun visitBigInt(ctx: BigIntContext) = BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())
        override fun visitFloat(ctx: FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())
        override fun visitBigFloat(ctx: BigFloatContext) = BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

        override fun visitSymbol(ctx: SymbolContext) = SymbolForm(ctx.sym().accept(SymVisitor))
        override fun visitQSymbol(ctx: QSymbolContext) = QSymbolForm(ctx.qsym().accept(QSymVisitor))

        override fun visitList(ctx: ListContext) = ListForm(ctx.form().map(::transformForm))
        override fun visitVector(ctx: VectorContext) = VectorForm(ctx.form().map(::transformForm))
        override fun visitSet(ctx: SetContext) = SetForm(ctx.form().map(::transformForm))
        override fun visitRecord(ctx: RecordContext) = RecordForm(ctx.form().map(::transformForm))

        override fun visitQuote(ctx: QuoteContext) = quoteForm(transformForm(ctx.form()))
        override fun visitSyntaxQuote(ctx: SyntaxQuoteContext) = syntaxQuoteForm(transformForm(ctx.form()))
        override fun visitUnquote(ctx: UnquoteContext) = ListForm(listOf(unquoteForm, transformForm(ctx.form())))
        override fun visitUnquoteSplicing(ctx: UnquoteSplicingContext) = ListForm(listOf(unquoteSplicingForm, transformForm(ctx.form())))
    }).withLoc(makeLoc(formContext))

    fun readForms(charStream: CharStream) =
        FormParser(CommonTokenStream(FormLexer(charStream)))
            .file().form()
            .toList()
            .map(::transformForm)

    companion object {
        internal fun readSymbol(s: String): Symbol =
            FormParser(CommonTokenStream(FormLexer(CharStreams.fromString(s)))).sym().accept(SymVisitor)

        internal fun readQSymbol(s: String): QSymbol =
            FormParser(CommonTokenStream(FormLexer(CharStreams.fromString(s)))).qsym().accept(QSymVisitor)

        internal fun readSourceForms(source: Source) =
            FormReader(source).readForms(CharStreams.fromReader(source.reader))
    }
}
