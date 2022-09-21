package brj

import brj.runtime.QSymbol
import brj.runtime.Symbol
import com.oracle.truffle.api.source.SourceSection
import java.util.*

sealed class Form : Zippable<Form> {
    abstract val loc: SourceSection?

    override val children: List<Form> = emptyList()
}

internal class NilForm(override val loc: SourceSection?) : Form() {
    override fun equals(other: Any?) = other is NilForm
    override fun hashCode() = NilForm::class.java.hashCode()
    override fun toString() = "nil"
}

internal class IntForm(val int: Int, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is IntForm && int == other.int)
    override fun hashCode() = int
    override fun toString() = int.toString()
}

internal class BoolForm(val bool: Boolean, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is BoolForm && bool == other.bool)
    override fun hashCode() = bool.hashCode()
    override fun toString() = bool.toString()
}

internal class StringForm(val string: String, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is StringForm && string == other.string)
    override fun hashCode() = string.hashCode()

    private val stringRep by lazy {
        // TODO re-escape
        "\"$string\""
    }

    override fun toString() = stringRep
}

internal class SymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is SymbolForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = sym.toString()
}

internal class DotSymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is DotSymbolForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = ".$sym"
}

internal class SymbolDotForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is SymbolDotForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = "$sym."
}

internal class QSymbolForm(val sym: QSymbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is QSymbolForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = sym.toString()
}

internal class DotQSymbolForm(val sym: QSymbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is DotQSymbolForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = "${sym.ns}/.${sym.local}"
}

internal class QSymbolDotForm(val sym: QSymbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is QSymbolDotForm && sym == other.sym)
    override fun hashCode() = sym.hashCode()

    override fun toString() = "$sym."
}

internal class KeywordForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is KeywordForm && sym == other.sym)
    override fun hashCode() = Objects.hash(sym)
    override fun toString() = ":$sym"
}

internal class KeywordDotForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is KeywordDotForm && sym == other.sym)
    override fun hashCode() = Objects.hash(sym)
    override fun toString() = ":$sym."
}

internal class QKeywordForm(val sym: QSymbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is QKeywordForm && sym == other.sym)
    override fun hashCode() = Objects.hash(sym)
    override fun toString() = ":$sym"
}

internal class QKeywordDotForm(val sym: QSymbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = this === other || (other is QKeywordDotForm && sym == other.sym)
    override fun hashCode() = Objects.hash(sym)
    override fun toString() = ":$sym."
}

internal class ListForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = (this === other) || (other is ListForm && forms == other.forms)
    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "(", separator = " ", postfix = ")")
    }

    override val children = forms
}

class VectorForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = (this === other) || (other is VectorForm && forms == other.forms)
    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "[", separator = " ", postfix = "]")
    }

    override val children = forms
}

class SetForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = (this === other) || (other is SetForm && forms == other.forms)
    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "#{", separator = " ", postfix = "}")
    }

    override val children = forms
}

class RecordForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = (this === other) || (other is RecordForm && forms == other.forms)
    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "{", separator = " ", postfix = "}")
    }

    override val children = forms
}