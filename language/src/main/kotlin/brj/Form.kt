package brj

import com.oracle.truffle.api.source.SourceSection

sealed class Form {
    abstract val sourceSection: SourceSection?
}

class IntForm(val int: Int, override val sourceSection: SourceSection? = null) : Form() {
    override fun toString() = int.toString()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is IntForm -> false
        else -> int == other.int
    }

    override fun hashCode() = int
}

class BoolForm(val bool: Boolean, override val sourceSection: SourceSection? = null) : Form() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is BoolForm -> false
        else -> bool == other.bool
    }

    override fun hashCode() = bool.hashCode()

    override fun toString() = bool.toString()
}

class StringForm(val string: String, override val sourceSection: SourceSection? = null) : Form() {
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

class ListForm(val forms: List<Form>, override val sourceSection: SourceSection? = null) : Form() {
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

class VectorForm(val forms: List<Form>, override val sourceSection: SourceSection? = null) : Form() {
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

class SetForm(val forms: List<Form>, override val sourceSection: SourceSection? = null) : Form() {
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

class RecordForm(val forms: List<Form>, override val sourceSection: SourceSection? = null) : Form() {
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
