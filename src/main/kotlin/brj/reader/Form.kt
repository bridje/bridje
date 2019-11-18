package brj.reader

import brj.Loc
import brj.runtime.QSymbol
import brj.runtime.Symbol
import java.math.BigDecimal
import java.math.BigInteger

internal val FORM_NS = Symbol.mkSym("brj.forms")

internal val UNQUOTE = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote"))
internal val UNQUOTE_SPLICING = QSymbol.mkQSym(FORM_NS, Symbol.mkSym("unquote-splicing"))

internal sealed class Form(val arg: Any) {
    var loc: Loc? = null

    fun withLoc(loc: Loc?): Form {
        this.loc = loc;
        return this
    }

    val qsym by lazy {
        QSymbol.mkQSym(FORM_NS, Symbol.mkSym(":${javaClass.simpleName}"))
    }
}

internal data class BooleanForm(val bool: Boolean) : Form(bool)
internal data class StringForm(val string: String) : Form(string)
internal data class IntForm(val int: Long) : Form(int)
internal data class BigIntForm(val bigInt: BigInteger) : Form(bigInt)
internal data class FloatForm(val float: Double) : Form(float)
internal data class BigFloatForm(val bigFloat: BigDecimal) : Form(bigFloat)
internal data class SymbolForm(val sym: Symbol) : Form(sym)
internal data class QSymbolForm(val sym: QSymbol) : Form(sym)
internal data class ListForm(val forms: List<Form>) : Form(forms)
internal data class VectorForm(val forms: List<Form>) : Form(forms)
internal data class SetForm(val forms: List<Form>) : Form(forms)
internal data class RecordForm(val forms: List<Form>) : Form(forms)
internal data class QuotedSymbolForm(val sym: Symbol) : Form(sym)
internal data class QuotedQSymbolForm(val sym: QSymbol) : Form(sym)
internal data class SyntaxQuotedSymbolForm(val sym: Symbol) : Form(sym)
internal data class SyntaxQuotedQSymbolForm(val sym: QSymbol) : Form(sym)

fun main() {
    data class Foo(val a: Int) {
        var b: String? = "foo"
    }

    val foo1 = Foo(5)
    foo1.b = "bar"

    val foo2 = Foo(5)

    println(foo1 == foo2)
}
