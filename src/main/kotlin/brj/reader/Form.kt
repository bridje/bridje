package brj.reader

import brj.Loc
import brj.emitter.QSymbol
import brj.emitter.Symbol
import java.math.BigDecimal
import java.math.BigInteger

internal val FORM_NS = Symbol.mkSym("brj.forms")

internal val UNQUOTE = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote"))
internal val UNQUOTE_SPLICING = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote-splicing"))

sealed class Form(internal val arg: Any) {
    abstract val loc: Loc?

    val qsym by lazy {
        QSymbol.mkQSym(FORM_NS, Symbol.mkSym(":${javaClass.simpleName}"))
    }
}

data class BooleanForm(override val loc: Loc?, val bool: Boolean) : Form(bool)
data class StringForm(override val loc: Loc?, val string: String) : Form(string)
data class IntForm(override val loc: Loc?, val int: Long) : Form(int)
data class BigIntForm(override val loc: Loc?, val bigInt: BigInteger) : Form(bigInt)
data class FloatForm(override val loc: Loc?, val float: Double) : Form(float)
data class BigFloatForm(override val loc: Loc?, val bigFloat: BigDecimal) : Form(bigFloat)
data class SymbolForm(override val loc: Loc?, val sym: Symbol) : Form(sym)
data class QSymbolForm(override val loc: Loc?, val sym: QSymbol) : Form(sym)
data class ListForm(override val loc: Loc?, val forms: List<Form>) : Form(forms)
data class VectorForm(override val loc: Loc?, val forms: List<Form>) : Form(forms)
data class SetForm(override val loc: Loc?, val forms: List<Form>) : Form(forms)
data class RecordForm(override val loc: Loc?, val forms: List<Form>) : Form(forms)
data class QuotedSymbolForm(override val loc: Loc?, val sym: Symbol) : Form(sym)
data class QuotedQSymbolForm(override val loc: Loc?, val sym: QSymbol) : Form(sym)
data class SyntaxQuotedSymbolForm(override val loc: Loc?, val sym: Symbol) : Form(sym)
data class SyntaxQuotedQSymbolForm(override val loc: Loc?, val sym: QSymbol) : Form(sym)

