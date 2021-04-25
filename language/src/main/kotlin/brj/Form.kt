package brj

import brj.runtime.Symbol
import com.oracle.truffle.api.source.SourceSection
import java.util.*

sealed class Form {
    abstract val loc: SourceSection?
}

internal class NilForm(override val loc: SourceSection?) : Form() {
    override fun equals(other: Any?) = other is NilForm
    override fun hashCode() = NilForm::class.java.hashCode()
    override fun toString() = "nil"
}

internal class IntForm(val int: Int, override val loc: SourceSection? = null) : Form() {
    override fun toString() = int.toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is IntForm -> false
        else -> int == other.int
    }

    override fun hashCode() = int
}

internal class BoolForm(val bool: Boolean, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is BoolForm -> false
        else -> bool == other.bool
    }

    override fun hashCode() = bool.hashCode()

    override fun toString() = bool.toString()
}

internal class StringForm(val string: String, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is StringForm -> false
        else -> string == other.string
    }

    override fun hashCode() = string.hashCode()

    private val stringRep by lazy {
        // TODO re-escape
        "\"$string\""
    }

    override fun toString() = stringRep
}

internal class SymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is SymbolForm -> false
        else -> sym == other.sym
    }

    override fun hashCode() = sym.hashCode()

    override fun toString() = sym.toString()
}

internal class DotSymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DotSymbolForm -> false
        else -> sym == other.sym
    }

    override fun hashCode() = sym.hashCode()

    override fun toString() = if (sym.ns != null) "${sym.ns}/.${sym.local}" else ".${sym.local}"
}


internal class SymbolDotForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is SymbolDotForm -> false
        else -> sym == other.sym
    }

    override fun hashCode() = sym.hashCode()

    override fun toString() = if (sym.ns != null) "${sym.ns}/${sym.local}." else "${sym.local}."
}

internal class KeywordForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) =
        this === other || (other is KeywordForm && sym == other.sym)

    override fun hashCode() = Objects.hash(sym)

    override fun toString() = ":$sym"
}

internal class KeywordDotForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) =
        this === other || (other is KeywordDotForm && sym == other.sym)

    override fun hashCode() = Objects.hash(sym)

    override fun toString() = ":$sym."
}

internal class ListForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is ListForm -> false
        else -> forms == other.forms
    }

    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "(", separator = " ", postfix = ")")
    }
}

class VectorForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is VectorForm -> false
        else -> forms == other.forms
    }

    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "[", separator = " ", postfix = "]")
    }
}

class SetForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is SetForm -> false
        else -> forms == other.forms
    }

    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "#{", separator = " ", postfix = "}")
    }
}

class RecordForm(val forms: List<Form>, override val loc: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is RecordForm -> false
        else -> forms == other.forms
    }

    override fun hashCode() = forms.hashCode()

    override fun toString(): String {
        return forms.joinToString(prefix = "{", separator = " ", postfix = "}")
    }
}