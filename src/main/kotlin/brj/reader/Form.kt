package brj.reader

import brj.Loc
import brj.runtime.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol.Companion.mkSym
import brj.types.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

internal val FORM_NS = mkSym("brj.forms")

internal val UNQUOTE = mkQSym(FORM_NS, mkSym("unquote"))
internal val UNQUOTE_SPLICING = mkQSym(FORM_NS, mkSym("unquote-splicing"))

internal sealed class Form(variantKey: VariantKey, arg: Any) : VariantObject(variantKey, arrayOf(arg)) {
    var loc: Loc? = null

    fun withLoc(loc: Loc?): Form {
        this.loc = loc
        return this
    }
}

private fun formVariantKey(sym: Symbol, paramType: MonoType) = VariantKey(mkQSym(FORM_NS, sym), emptyList(), listOf(paramType))

internal val FORM = mkSym("Form")

internal val FORM_TYPE_ALIAS = TypeAlias_(mkQSym(FORM_NS, FORM), type = null)
private val LIST_OF_FORMS = VectorType(TypeAliasType(FORM_TYPE_ALIAS, emptyList()))

private val BOOLEAN_KEY = formVariantKey(mkSym(":BooleanForm"), BoolType)
private val STRING_KEY = formVariantKey(mkSym(":StringForm"), StringType)
private val INT_KEY = formVariantKey(mkSym(":IntForm"), IntType)
private val BIGINT_KEY = formVariantKey(mkSym(":BigIntForm"), BigIntType)
private val FLOAT_KEY = formVariantKey(mkSym(":FloatForm"), FloatType)
private val BIGFLOAT_KEY = formVariantKey(mkSym(":BigFloatForm"), BigFloatType)
private val SYMBOL_KEY = formVariantKey(mkSym(":SymbolForm"), SymbolType)
private val QSYMBOL_KEY = formVariantKey(mkSym(":QSymbolForm"), QSymbolType)
private val LIST_KEY = formVariantKey(mkSym(":ListForm"), LIST_OF_FORMS)
private val VECTOR_KEY = formVariantKey(mkSym(":VectorForm"), LIST_OF_FORMS)
private val SET_KEY = formVariantKey(mkSym(":SetForm"), LIST_OF_FORMS)
private val RECORD_KEY = formVariantKey(mkSym(":RecordForm"), LIST_OF_FORMS)
private val QUOTED_SYMBOL_KEY = formVariantKey(mkSym(":Form"), SymbolType)
private val QUOTED_QSYMBOL_KEY = formVariantKey(mkSym(":Form"), QSymbolType)
private val SYNTAX_QUOTED_SYMBOL_KEY = formVariantKey(mkSym(":Form"), SymbolType)
private val SYNTAX_QUOTED_QSYMBOL_KEY = formVariantKey(mkSym(":Form"), QSymbolType)

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

internal data class MetaForm(val variantKey: VariantKey, val clazz: KClass<*>)

internal val META_FORMS = listOf(
    MetaForm(BOOLEAN_KEY, BooleanForm::class),
    MetaForm(STRING_KEY, StringForm::class),
    MetaForm(INT_KEY, IntForm::class),
    MetaForm(BIGINT_KEY, BigIntForm::class),
    MetaForm(FLOAT_KEY, FloatForm::class),
    MetaForm(BIGFLOAT_KEY, BigFloatForm::class),
    MetaForm(SYMBOL_KEY, SymbolForm::class),
    MetaForm(QSYMBOL_KEY, QSymbolForm::class),
    MetaForm(LIST_KEY, ListForm::class),
    MetaForm(VECTOR_KEY, VectorForm::class),
    MetaForm(SET_KEY, SetForm::class),
    MetaForm(RECORD_KEY, RecordForm::class),
    MetaForm(QUOTED_SYMBOL_KEY, QuotedSymbolForm::class),
    MetaForm(QUOTED_QSYMBOL_KEY, QuotedQSymbolForm::class),
    MetaForm(SYNTAX_QUOTED_SYMBOL_KEY, SyntaxQuotedSymbolForm::class),
    MetaForm(SYNTAX_QUOTED_QSYMBOL_KEY, SyntaxQuotedQSymbolForm::class)
).also { metaForms ->
    FORM_TYPE_ALIAS.type = VariantType(
        metaForms.associate { it.variantKey to RowKey(emptyList()) },
        RowTypeVar(false))
}
