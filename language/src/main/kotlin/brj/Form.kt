package brj

import brj.runtime.BridjeVector
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString
import java.math.BigDecimal
import java.math.BigInteger

// Meta objects for each form type
@ExportLibrary(InteropLibrary::class)
object SymbolMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Symbol", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is SymbolForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Symbol"
}

@ExportLibrary(InteropLibrary::class)
object QualifiedSymbolMeta : TruffleObject {
    private val name = TruffleString.fromConstant("QualifiedSymbol", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is QualifiedSymbolForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "QualifiedSymbol"
}

@ExportLibrary(InteropLibrary::class)
object ListMeta : TruffleObject {
    private val name = TruffleString.fromConstant("List", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is ListForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "List"
}

@ExportLibrary(InteropLibrary::class)
object VectorMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Vector", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is VectorForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Vector"
}

@ExportLibrary(InteropLibrary::class)
object MapMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Map", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is MapForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Map"
}

@ExportLibrary(InteropLibrary::class)
object SetMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Set", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is SetForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Set"
}

@ExportLibrary(InteropLibrary::class)
object IntMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Int", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is IntForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Int"
}

@ExportLibrary(InteropLibrary::class)
object DoubleMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Double", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is DoubleForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Double"
}

@ExportLibrary(InteropLibrary::class)
object StringMeta : TruffleObject {
    private val name = TruffleString.fromConstant("String", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is StringForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "String"
}

@ExportLibrary(InteropLibrary::class)
object KeywordMeta : TruffleObject {
    private val name = TruffleString.fromConstant("Keyword", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is KeywordForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "Keyword"
}

@ExportLibrary(InteropLibrary::class)
object BigIntMeta : TruffleObject {
    private val name = TruffleString.fromConstant("BigInt", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is BigIntForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "BigInt"
}

@ExportLibrary(InteropLibrary::class)
object BigDecMeta : TruffleObject {
    private val name = TruffleString.fromConstant("BigDec", TruffleString.Encoding.UTF_8)

    @ExportMessage fun isMetaObject() = true
    @ExportMessage fun getMetaSimpleName(): Any = name
    @ExportMessage fun getMetaQualifiedName(): Any = name
    @ExportMessage fun isMetaInstance(instance: Any?) = instance is BigDecForm

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "BigDec"
}

sealed interface Form : TruffleObject {
    val loc: SourceSection?
    override fun toString(): String
}

internal fun String.reescape() = this
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
    .replace("\"", "\\\"")

@ExportLibrary(InteropLibrary::class)
class IntForm(val value: Long, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = value.toString()

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = IntMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class DoubleForm(val value: Double, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = value.toString()

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = DoubleMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class BigIntForm(val value: BigInteger, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "${value}N"

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = BigIntMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class BigDecForm(val value: BigDecimal, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "${value}M"

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = BigDecMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class StringForm(val value: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "\"${value.reescape()}\""

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = StringMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class SymbolForm(val name: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = name

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = SymbolMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class QualifiedSymbolForm(val namespace: String, val member: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = "$namespace/$member"

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = QualifiedSymbolMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class KeywordForm(val name: String, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = ":$name"

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = KeywordMeta

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class ListForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "(", separator = " ", postfix = ")")

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = ListMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els.toTypedArray())
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class VectorForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = VectorMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els.toTypedArray())
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class SetForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = SetMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els.toTypedArray())
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

@ExportLibrary(InteropLibrary::class)
class MapForm(val els: List<Form>, override val loc: SourceSection? = null) : Form {
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = MapMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els.toTypedArray())
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}
