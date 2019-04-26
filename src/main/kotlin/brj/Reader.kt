package brj

import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.analyser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger

private val formNS = mkSym("brj.forms")

private val UNQUOTE = mkQSym(formNS, mkSym("unquote"))
private val UNQUOTE_SPLICING = mkQSym(formNS, mkSym("unquote-splicing"))

sealed class Form(internal val arg: Any) {
    internal val qsym: QSymbol = mkQSym(formNS, mkSym(":${this.javaClass.simpleName}"))
}

data class BooleanForm(val bool: Boolean) : Form(bool)
data class StringForm(val string: String) : Form(string)
data class IntForm(val int: Long) : Form(int)
data class BigIntForm(val bigInt: BigInteger) : Form(bigInt)
data class FloatForm(val float: Double) : Form(float)
data class BigFloatForm(val bigFloat: BigDecimal) : Form(bigFloat)
data class SymbolForm(val sym: Symbol) : Form(sym)
data class QSymbolForm(val sym: QSymbol) : Form(sym)
data class ListForm(val forms: List<Form>) : Form(forms)
data class VectorForm(val forms: List<Form>) : Form(forms)
data class SetForm(val forms: List<Form>) : Form(forms)
data class RecordForm(val forms: List<Form>) : Form(forms)
data class QuotedSymbolForm(val sym: Symbol) : Form(sym)
data class QuotedQSymbolForm(val sym: QSymbol) : Form(sym)
data class SyntaxQuotedSymbolForm(val sym: Symbol) : Form(sym)
data class SyntaxQuotedQSymbolForm(val sym: QSymbol) : Form(sym)

private fun quoteForm(form: Form): Form {
    fun q(argForm: Form) = ListForm(listOf(QSymbolForm(form.qsym), argForm))

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

private val CONCAT_QSYM_FORM = QSymbolForm(mkQSym(formNS, mkSym("concat")))

private fun syntaxQuoteForm(form: Form, splicing: Boolean = false): Form {
    fun firstQSym(forms: List<Form>): QSymbol? = (forms[0] as? QSymbolForm)?.sym

    fun sq(argForm: Form) = ListForm(listOf(QSymbolForm(form.qsym), argForm))

    fun sqSeq(forms: List<Form>): Form {
        val nestedSplicingForm = forms.any { it is ListForm && firstQSym(it.forms) == UNQUOTE_SPLICING }
        val expandedForms = VectorForm(forms.map { syntaxQuoteForm(it, nestedSplicingForm) })
        return sq(if (nestedSplicingForm) ListForm(listOf(CONCAT_QSYM_FORM, expandedForms)) else expandedForms)
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

private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

    override fun visitBoolean(ctx: FormParser.BooleanContext): Form = BooleanForm(ctx.text == "true")

    override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

    override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong())

    override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
        BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

    override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())

    override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
        BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

    override fun visitSymbol(ctx: FormParser.SymbolContext): Form = SymbolForm(mkSym(ctx.text))
    override fun visitQSymbol(ctx: FormParser.QSymbolContext): Form = QSymbolForm(mkQSym(ctx.text))

    override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(::transformForm))
    override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(::transformForm))
    override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(::transformForm))
    override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(::transformForm))

    override fun visitQuote(ctx: FormParser.QuoteContext) = quoteForm(transformForm(ctx.form()))
    override fun visitSyntaxQuote(ctx: FormParser.SyntaxQuoteContext) = syntaxQuoteForm(transformForm(ctx.form()))
    override fun visitUnquote(ctx: FormParser.UnquoteContext): Form = ListForm(listOf(QSymbolForm(UNQUOTE), transformForm(ctx.form())))
    override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = ListForm(listOf(QSymbolForm(mkQSym("brj.forms/unquote-splicing")), transformForm(ctx.form())))
})

fun readForms(reader: Reader): List<Form> =
    FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(reader))))
        .file().form()
        .toList()
        .map(::transformForm)

fun readForms(s: String): List<Form> = readForms(StringReader(s))
