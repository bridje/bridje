package brj.reader

import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.VARIANT
import brj.types.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

internal val FORM_NS = Symbol(ID, "brj.forms")

internal val UNQUOTE = QSymbol(FORM_NS, "unquote")
internal val UNQUOTE_SPLICING = QSymbol(FORM_NS, "unquote-splicing")

internal sealed class Form(formClass: KClass<out Form>, arg: Any) : VariantObject(FORM_TYPES.getValue(formClass).variantKey, arrayOf(arg)) {
    var loc: Loc? = null
    internal val stringRep by lazy {
        when (this) {
            is BooleanForm -> bool.toString()
            is StringForm -> string.replace(Regex("""[\\"\n\t\r]""")) {
                when (it.value.first()) {
                    '\\' -> "\\\\"
                    '\n' -> "\\\n"
                    '\t' -> "\\\t"
                    '\r' -> "\\\r"
                    '\"' -> "\\\""
                    else -> it.value
                }
            }
            is IntForm -> int.toString()
            is BigIntForm -> "${bigInt}N"
            is FloatForm -> float.toString()
            is BigFloatForm -> "${bigFloat}M"
            is SymbolForm -> sym.toString()
            is QSymbolForm -> sym.toString()
            is ListForm -> forms.joinToString(prefix = "(", separator = " ", postfix = ")")
            is VectorForm -> forms.joinToString(prefix = "[", separator = " ", postfix = "]")
            is SetForm -> forms.joinToString(prefix = "#{", separator = " ", postfix = "}")
            is RecordForm -> forms.joinToString(prefix = "{", separator = " ", postfix = "}")
            is QuotedSymbolForm -> "'${sym}"
            is QuotedQSymbolForm -> "'${sym}"
            is SyntaxQuotedSymbolForm -> "`${sym}"
            is SyntaxQuotedQSymbolForm -> "`${sym}"
        }
    }

    fun withLoc(loc: Loc?): Form {
        this.loc = loc
        return this
    }
}

internal val FORM = QSymbol(FORM_NS, "Form")

internal val FORM_TYPE_ALIAS = TypeAlias_(FORM, type = null)
private val LIST_OF_FORMS = VectorType(TypeAliasType(FORM_TYPE_ALIAS, emptyList()))

internal class BooleanForm(val bool: Boolean) : Form(BooleanForm::class, bool)
internal class StringForm(val string: String) : Form(StringForm::class, string)
internal class IntForm(val int: Long) : Form(IntForm::class, int)
internal class BigIntForm(val bigInt: BigInteger) : Form(BigIntForm::class, bigInt)
internal class FloatForm(val float: Double) : Form(FloatForm::class, float)
internal class BigFloatForm(val bigFloat: BigDecimal) : Form(BigFloatForm::class, bigFloat)
internal class SymbolForm(val sym: Symbol) : Form(SymbolForm::class, sym)
internal class QSymbolForm(val sym: QSymbol) : Form(QSymbolForm::class, sym)
internal class ListForm(val forms: List<Form>) : Form(ListForm::class, forms)
internal class VectorForm(val forms: List<Form>) : Form(VectorForm::class, forms)
internal class SetForm(val forms: List<Form>) : Form(SetForm::class, forms)
internal class RecordForm(val forms: List<Form>) : Form(RecordForm::class, forms)
internal class QuotedSymbolForm(val sym: Symbol) : Form(QuotedSymbolForm::class, sym)
internal class QuotedQSymbolForm(val sym: QSymbol) : Form(QuotedQSymbolForm::class, sym)
internal class SyntaxQuotedSymbolForm(val sym: Symbol) : Form(SyntaxQuotedSymbolForm::class, sym)
internal class SyntaxQuotedQSymbolForm(val sym: QSymbol) : Form(SyntaxQuotedQSymbolForm::class, sym)

internal data class FormType<I>(val formClass: KClass<*>, val constructor: (I) -> Form, val paramType: MonoType) {
    val variantKey = VariantKey(QSymbol(FORM_NS, Symbol(VARIANT, formClass.simpleName!!)), emptyList(), listOf(paramType))
}

internal val FORM_TYPES = listOf(
    FormType(BooleanForm::class, ::BooleanForm, BoolType),
    FormType(StringForm::class, ::StringForm, StringType),
    FormType(IntForm::class, ::IntForm, IntType),
    FormType(BigIntForm::class, ::BigIntForm, BigIntType),
    FormType(FloatForm::class, ::FloatForm, FloatType),
    FormType(BigFloatForm::class, ::BigFloatForm, BigFloatType),
    FormType(SymbolForm::class, ::SymbolForm, SymbolType),
    FormType(QSymbolForm::class, ::QSymbolForm, QSymbolType),
    FormType(ListForm::class, ::ListForm, LIST_OF_FORMS),
    FormType(VectorForm::class, ::VectorForm, LIST_OF_FORMS),
    FormType(SetForm::class, ::SetForm, LIST_OF_FORMS),
    FormType(RecordForm::class, ::RecordForm, LIST_OF_FORMS),
    FormType(QuotedSymbolForm::class, ::QuotedSymbolForm, SymbolType),
    FormType(SyntaxQuotedSymbolForm::class, ::SyntaxQuotedSymbolForm, SymbolType),
    FormType(SyntaxQuotedQSymbolForm::class, ::SyntaxQuotedQSymbolForm, QSymbolType))

    .associateBy { it.formClass }
    .also { formTypes ->
        FORM_TYPE_ALIAS.type = VariantType(
            formTypes.values.associate { it.variantKey to RowKey(emptyList()) },
            RowTypeVar(false))
    }
