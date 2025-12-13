package brj

import com.oracle.truffle.api.source.SourceSection
import java.math.BigDecimal
import java.math.BigInteger

sealed interface Form {
    val loc: SourceSection?
    override fun toString(): String
}

internal fun String.reescape() = this
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
    .replace("\"", "\\\"")

class IntForm(val value: Long, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = value.toString()
}

class DoubleForm(val value: Double, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = value.toString()
}

class BigIntForm(val value: BigInteger, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "${value}N"
}

class BigDecForm(val value: BigDecimal, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "${value}M"
}

class StringForm(val value: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "\"${value.reescape()}\""
}

class SymbolForm(val name: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = name
}

class QualifiedSymbolForm(val namespace: String, val member: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "$namespace/$member"
}

class KeywordForm(val name: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = ":$name"
}

class ListForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "(", separator = " ", postfix = ")")
}

class VectorForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")
}

class SetForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")
}

class MapForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")
}