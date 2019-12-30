package brj.reader

import brj.Loc
import brj.runtime.*
import brj.runtime.SymKind.*
import brj.runtime.TypeAlias_
import brj.runtime.VariantKey
import brj.runtime.VariantObject
import brj.types.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

internal val FORM_NS = Symbol(ID, "brj.forms")

internal val UNQUOTE = QSymbol(FORM_NS, ID, "unquote")
internal val UNQUOTE_SPLICING = QSymbol(FORM_NS, ID, "unquote-splicing")

internal sealed class Form(variantKey: VariantKey, arg: Any) : VariantObject(variantKey, arrayOf(arg)) {
    var loc: Loc? = null

    fun withLoc(loc: Loc?): Form {
        this.loc = loc
        return this
    }
}

private fun formVariantKey(clazz: KClass<*>, paramType: MonoType) = VariantKey(QSymbol(FORM_NS, Symbol(VARIANT, clazz.simpleName!!)), emptyList(), listOf(paramType))

internal val FORM = QSymbol(FORM_NS, TYPE, "Form")

internal val FORM_TYPE_ALIAS = TypeAlias_(FORM, type = null)
private val LIST_OF_FORMS = VectorType(TypeAliasType(FORM_TYPE_ALIAS, emptyList()))

private val BOOLEAN_KEY = formVariantKey(BooleanForm::class, BoolType)
private val STRING_KEY = formVariantKey(StringForm::class, StringType)
private val INT_KEY = formVariantKey(IntForm::class, IntType)
private val BIGINT_KEY = formVariantKey(BigIntForm::class, BigIntType)
private val FLOAT_KEY = formVariantKey(FloatForm::class, FloatType)
private val BIGFLOAT_KEY = formVariantKey(BigFloatForm::class, BigFloatType)
private val SYMBOL_KEY = formVariantKey(SymbolForm::class, SymbolType)
private val QSYMBOL_KEY = formVariantKey(QSymbolForm::class, QSymbolType)
private val LIST_KEY = formVariantKey(ListForm::class, LIST_OF_FORMS)
private val VECTOR_KEY = formVariantKey(VectorForm::class, LIST_OF_FORMS)
private val SET_KEY = formVariantKey(SetForm::class, LIST_OF_FORMS)
private val RECORD_KEY = formVariantKey(RecordForm::class, LIST_OF_FORMS)
private val QUOTED_SYMBOL_KEY = formVariantKey(QuotedSymbolForm::class, SymbolType)
private val QUOTED_QSYMBOL_KEY = formVariantKey(QuotedQSymbolForm::class, QSymbolType)
private val SYNTAX_QUOTED_SYMBOL_KEY = formVariantKey(SyntaxQuotedSymbolForm::class, SymbolType)
private val SYNTAX_QUOTED_QSYMBOL_KEY = formVariantKey(SyntaxQuotedQSymbolForm::class, QSymbolType)

internal class BooleanForm(val bool: Boolean) : Form(BOOLEAN_KEY, bool)
internal class StringForm(val string: String) : Form(STRING_KEY, string)
internal class IntForm(val int: Long) : Form(INT_KEY, int)
internal class BigIntForm(val bigInt: BigInteger) : Form(BIGINT_KEY, bigInt)
internal class FloatForm(val float: Double) : Form(FLOAT_KEY, float)
internal class BigFloatForm(val bigFloat: BigDecimal) : Form(BIGFLOAT_KEY, bigFloat)
internal class SymbolForm(val sym: Symbol) : Form(SYMBOL_KEY, sym)
internal class QSymbolForm(val sym: QSymbol) : Form(QSYMBOL_KEY, sym)
internal class ListForm(val forms: List<Form>) : Form(LIST_KEY, forms)
internal class VectorForm(val forms: List<Form>) : Form(VECTOR_KEY, forms)
internal class SetForm(val forms: List<Form>) : Form(SET_KEY, forms)
internal class RecordForm(val forms: List<Form>) : Form(RECORD_KEY, forms)
internal class QuotedSymbolForm(val sym: Symbol) : Form(QUOTED_SYMBOL_KEY, sym)
internal class QuotedQSymbolForm(val sym: QSymbol) : Form(QUOTED_QSYMBOL_KEY, sym)
internal class SyntaxQuotedSymbolForm(val sym: Symbol) : Form(SYNTAX_QUOTED_SYMBOL_KEY, sym)
internal class SyntaxQuotedQSymbolForm(val sym: QSymbol) : Form(SYNTAX_QUOTED_QSYMBOL_KEY, sym)

internal data class MetaForm(val clazz: KClass<*>, val paramType: MonoType) {
    val variantKey = formVariantKey(clazz, paramType)
}

internal val META_FORMS = listOf(
    MetaForm(BooleanForm::class, BoolType),
    MetaForm(StringForm::class, StringType),
    MetaForm(IntForm::class, IntType),
    MetaForm(BigIntForm::class, BigIntType),
    MetaForm(FloatForm::class, FloatType),
    MetaForm(BigFloatForm::class, BigFloatType),
    MetaForm(SymbolForm::class, SymbolType),
    MetaForm(ListForm::class, LIST_OF_FORMS),
    MetaForm(VectorForm::class, LIST_OF_FORMS),
    MetaForm(SetForm::class, LIST_OF_FORMS),
    MetaForm(RecordForm::class, LIST_OF_FORMS),
    MetaForm(QuotedSymbolForm::class, SymbolType),
    MetaForm(SyntaxQuotedSymbolForm::class, SymbolType)
).also { metaForms ->
    FORM_TYPE_ALIAS.type = VariantType(
        metaForms.associate { it.variantKey to RowKey(emptyList()) },
        RowTypeVar(false))
}
