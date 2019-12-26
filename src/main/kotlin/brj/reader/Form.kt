package brj.reader

import brj.Loc
import brj.runtime.*
import brj.runtime.QSymbol.Companion.mkQSym
import brj.runtime.Symbol.Companion.mkSym
import brj.types.*
import org.graalvm.polyglot.Value
import java.math.BigDecimal
import java.math.BigInteger

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

internal data class MetaForm<A : Any, F : Any>(val variantKey: VariantKey, val f: (A) -> F) {
    @Suppress("UNCHECKED_CAST")
    fun construct(arg: Any) = f(arg as A)
}

private fun <F> wrapHostObject(f: (List<Form>) -> F): (Any) -> F {
    return { f(Value.asValue(it).asHostObject()) }
}

internal val META_FORMS = listOf(
    MetaForm(BOOLEAN_KEY, ::BooleanForm),
    MetaForm(STRING_KEY, ::StringForm),
    MetaForm(INT_KEY, ::IntForm),
    MetaForm(BIGINT_KEY, ::BigIntForm),
    MetaForm(FLOAT_KEY, ::FloatForm),
    MetaForm(BIGFLOAT_KEY, ::BigFloatForm),
    MetaForm(SYMBOL_KEY, ::SymbolForm),
    MetaForm(QSYMBOL_KEY, ::QSymbolForm),
    MetaForm(LIST_KEY, wrapHostObject(::ListForm)),
    MetaForm(VECTOR_KEY, wrapHostObject(::VectorForm)),
    MetaForm(SET_KEY, wrapHostObject(::SetForm)),
    MetaForm(RECORD_KEY, wrapHostObject(::RecordForm)),
    MetaForm(QUOTED_SYMBOL_KEY, ::QuotedSymbolForm),
    MetaForm(QUOTED_QSYMBOL_KEY, ::QuotedQSymbolForm),
    MetaForm(SYNTAX_QUOTED_SYMBOL_KEY, ::SyntaxQuotedSymbolForm),
    MetaForm(SYNTAX_QUOTED_QSYMBOL_KEY, ::SyntaxQuotedQSymbolForm)
).also { metaForms ->
    FORM_TYPE_ALIAS.type = VariantType(
        metaForms.associate { it.variantKey to RowKey(emptyList()) },
        RowTypeVar(false))
}
