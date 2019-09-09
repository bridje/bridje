package brj.reader

import brj.BridjeLanguage
import brj.Loc
import brj.emitter.QSymbol
import brj.emitter.Symbol
import java.math.BigDecimal
import java.math.BigInteger

internal val FORM_NS = Symbol.mkSym("brj.forms")

internal val UNQUOTE = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote"))
internal val UNQUOTE_SPLICING = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote-splicing"))

internal sealed class Form(val arg: Any, val loc: Loc?) {
    val qsym by lazy {
        QSymbol.mkQSym(FORM_NS, Symbol.mkSym(":${javaClass.simpleName}"))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Form) return false
        if (this.javaClass != other.javaClass) return false
        if (arg != other.arg) return false
        return true
    }

    override fun hashCode(): Int {
        return arg.hashCode()
    }

    open fun argToString(): String = arg.toString()

    private val stringRep by lazy {
        "(:${javaClass.simpleName} ${BridjeLanguage.toString(arg)})"
    }

    override fun toString() = stringRep
}

internal class BooleanForm(val bool: Boolean, loc: Loc?) : Form(bool, loc)
internal class StringForm(val string: String, loc: Loc?) : Form(string, loc)
internal class IntForm(val int: Long, loc: Loc?) : Form(int, loc)
internal class BigIntForm(val bigInt: BigInteger, loc: Loc?) : Form(bigInt, loc)
internal class FloatForm(val float: Double, loc: Loc?) : Form(float, loc)
internal class BigFloatForm(val bigFloat: BigDecimal, loc: Loc?) : Form(bigFloat, loc)
internal class SymbolForm(val sym: Symbol, loc: Loc?) : Form(sym, loc)
internal class QSymbolForm(val sym: QSymbol, loc: Loc?) : Form(sym, loc)
internal class ListForm(val forms: List<Form>, loc: Loc?) : Form(forms, loc)
internal class VectorForm(val forms: List<Form>, loc: Loc?) : Form(forms, loc)
internal class SetForm(val forms: List<Form>, loc: Loc?) : Form(forms, loc)
internal class RecordForm(val forms: List<Form>, loc: Loc?) : Form(forms, loc)
internal class QuotedSymbolForm(val sym: Symbol, loc: Loc?) : Form(sym, loc)
internal class QuotedQSymbolForm(val sym: QSymbol, loc: Loc?) : Form(sym, loc)
internal class SyntaxQuotedSymbolForm(val sym: Symbol, loc: Loc?) : Form(sym, loc)
internal class SyntaxQuotedQSymbolForm(val sym: QSymbol, loc: Loc?) : Form(sym, loc)

