package brj

import java.math.BigDecimal
import java.math.BigInteger

sealed class Form {

    data class BooleanForm(val bool: Boolean) : Form()
    data class StringForm(val string: String) : Form()
    data class IntForm(val int: Long) : Form()
    data class BigIntForm(val bigInt: BigInteger) : Form()
    data class FloatForm(val float: Double) : Form()
    data class BigFloatForm(val bigFloat: BigDecimal) : Form()
    data class SymbolForm(val sym: Symbol) : Form()
    data class NamespacedSymbolForm(val sym: NamespacedSymbol) : Form()
    data class KeywordForm(val kw: Keyword) : Form()
    data class NamespacedKeywordForm(val kw: NamespacedKeyword) : Form()
    data class ListForm(val forms: List<Form>) : Form()
    data class VectorForm(val forms: List<Form>) : Form()
    data class SetForm(val forms: List<Form>) : Form()
    data class RecordForm(val forms: List<Form>) : Form()
    data class QuoteForm(val form: Form) : Form()
    data class UnquoteForm(val form: Form) : Form()
    data class UnquoteSplicingForm(val form: Form) : Form()


}