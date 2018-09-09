package brj.forms

import brj.grammar.FormBaseVisitor
import brj.grammar.FormLexer
import brj.grammar.FormParser
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.math.BigDecimal
import java.math.BigInteger

abstract class Form {

    interface FormVisitor<T> {
        fun visit(form: BooleanForm): T
        fun visit(form: StringForm): T
        fun visit(form: IntForm): T
        fun visit(form: BigIntForm): T
        fun visit(form: FloatForm): T
        fun visit(form: BigFloatForm): T
        fun visit(form: SymbolForm): T
        fun visit(form: KeywordForm): T
        fun visit(form: ListForm): T
        fun visit(form: VectorForm): T
        fun visit(form: SetForm): T
        fun visit(form: RecordForm): T
        fun visit(form: QuoteForm): T
        fun visit(form: UnquoteForm): T
        fun visit(form: UnquoteSplicingForm): T
    }

    abstract fun <T> accept(visitor: FormVisitor<T>): T;

    data class BooleanForm(val bool: Boolean) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this);
    }

    data class StringForm(val string: String) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class IntForm(val int: Int) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class BigIntForm(val bigInt: BigInteger) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class FloatForm(val float: Float) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class BigFloatForm(val bigFloat: BigDecimal) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class SymbolForm(val ns: String?, val kw: String) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class KeywordForm(val ns: String?, val kw: String) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class ListForm(val forms: List<Form>) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class VectorForm(val forms: List<Form>) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class SetForm(val forms: List<Form>) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class RecordForm(val forms: List<Form>) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class QuoteForm(val form: Form) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class UnquoteForm(val form: Form) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    data class UnquoteSplicingForm(val form: Form) : Form() {
        override fun <T> accept(visitor: FormVisitor<T>): T = visitor.visit(this)
    }

    companion object {
        private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

            override fun visitBoolean(ctx: FormParser.BooleanContext): Form = Form.BooleanForm(ctx.text == "true")

            override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

            override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toInt())

            override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
                BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

            override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toFloat())

            override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
                BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

            override fun visitSymbol(ctx: FormParser.SymbolContext) =
                Regex("((.+)/)?(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> SymbolForm(groups[2]?.value, groups[3]!!.value) }

            override fun visitKeyword(ctx: FormParser.KeywordContext) =
                Regex(":((.+)/)?(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> KeywordForm(groups[2]?.value, groups[3]!!.value) }

            override fun visitList(ctx: FormParser.ListContext) = Form.ListForm(ctx.form().map(::transformForm))
            override fun visitVector(ctx: FormParser.VectorContext) = Form.VectorForm(ctx.form().map(::transformForm))
            override fun visitSet(ctx: FormParser.SetContext) = Form.SetForm(ctx.form().map(::transformForm))
            override fun visitRecord(ctx: FormParser.RecordContext) = Form.RecordForm(ctx.form().map(::transformForm))

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
}