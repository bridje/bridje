package brj

import brj.runtime.BridjeRecord
import brj.runtime.BridjeVector
import brj.runtime.BuiltinMetaObj
import brj.runtime.Loc
import brj.runtime.Meta
import brj.runtime.Symbol
import brj.runtime.sym
import com.oracle.truffle.api.interop.ArityException
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
object SymbolFormMeta : BuiltinMetaObj("SymbolForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is SymbolForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val sym = arguments[0] as Symbol
        return SymbolForm(sym)
    }
}

object QSymbolFormMeta : BuiltinMetaObj("QSymbolForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is QSymbolForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 2) throw ArityException.create(2, 2, arguments.size)
        val ns = arguments[0] as Symbol
        val member = arguments[1] as Symbol
        return QSymbolForm(ns, member)
    }
}

object ListMeta : BuiltinMetaObj("List".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is ListForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val vec = arguments[0] as BridjeVector
        return ListForm(vec.toFormList())
    }
}

object VectorMeta : BuiltinMetaObj("Vector".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is VectorForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val vec = arguments[0] as BridjeVector
        return VectorForm(vec.toFormList())
    }
}

object RecordMeta : BuiltinMetaObj("Record".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is RecordForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val vec = arguments[0] as BridjeVector
        return RecordForm(vec.toFormList())
    }
}

object SetMeta : BuiltinMetaObj("Set".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is SetForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val vec = arguments[0] as BridjeVector
        return SetForm(vec.toFormList())
    }
}

object IntMeta : BuiltinMetaObj("Int".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is IntForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        return IntForm(arguments[0] as Long)
    }
}

object DoubleMeta : BuiltinMetaObj("Double".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is DoubleForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        return DoubleForm(arguments[0] as Double)
    }
}

object StringMeta : BuiltinMetaObj("String".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is StringForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val str = arguments[0] as TruffleString
        return StringForm(str.toJavaStringUncached())
    }
}

object BigIntMeta : BuiltinMetaObj("BigInt".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is BigIntForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        return BigIntForm(arguments[0] as BigInteger)
    }
}

object BigDecMeta : BuiltinMetaObj("BigDec".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is BigDecForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        return BigDecForm(arguments[0] as BigDecimal)
    }
}

object KeywordFormMeta : BuiltinMetaObj("KeywordForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is KeywordForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val sym = arguments[0] as Symbol
        return KeywordForm(sym)
    }
}

object QKeywordFormMeta : BuiltinMetaObj("QKeywordForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is QKeywordForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 2) throw ArityException.create(2, 2, arguments.size)
        val ns = arguments[0] as Symbol
        val member = arguments[1] as Symbol
        return QKeywordForm(ns, member)
    }
}

object DotSymbolFormMeta : BuiltinMetaObj("DotSymbolForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is DotSymbolForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val sym = arguments[0] as Symbol
        return DotSymbolForm(sym)
    }
}

object QDotSymbolFormMeta : BuiltinMetaObj("QDotSymbolForm".sym, "brj.rdr".sym) {
    override fun isMetaInstance(instance: Any?) = instance is QDotSymbolForm

    @Throws(ArityException::class)
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 2) throw ArityException.create(2, 2, arguments.size)
        val ns = arguments[0] as Symbol
        val member = arguments[1] as Symbol
        return QDotSymbolForm(ns, member)
    }
}

@ExportLibrary(InteropLibrary::class)
sealed class Form : TruffleObject, Meta<Form> {
    abstract val loc: SourceSection?
    abstract val metaObj: BuiltinMetaObj

    var staticMeta: RecordForm? = null
        protected set

    private var _meta: BridjeRecord? = null

    override val meta: BridjeRecord
        get() = _meta ?: loc?.let { BridjeRecord.EMPTY.put("loc", Loc(it)) } ?: BridjeRecord.EMPTY

    abstract fun copy(): Form

    fun withStaticMeta(keyword: KeywordForm): Form =
        withStaticMeta(RecordForm(listOf(keyword, SymbolForm("true".sym)), keyword.loc))

    fun withStaticMeta(keyword: QKeywordForm): Form =
        withStaticMeta(RecordForm(listOf(keyword, SymbolForm("true".sym)), keyword.loc))

    fun withStaticMeta(record: RecordForm): Form = copy().also {
        it.staticMeta = if (staticMeta == null) record else RecordForm(staticMeta!!.els + record.els, record.loc)
        it._meta = this._meta
    }

    override fun withMeta(newMeta: BridjeRecord?): Form = copy().also {
        it._meta = newMeta
        it.staticMeta = this.staticMeta
    }

    abstract override fun toString(): String

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = metaObj

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean): String = toString()
}

internal fun String.reescape() = this
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
    .replace("\"", "\\\"")

class IntForm(val value: Long, override val loc: SourceSection? = null) : Form() {
    override val metaObj = IntMeta
    override fun copy() = IntForm(value, loc)
    override fun toString(): String = value.toString()
}

class DoubleForm(val value: Double, override val loc: SourceSection? = null) : Form() {
    override val metaObj = DoubleMeta
    override fun copy() = DoubleForm(value, loc)
    override fun toString(): String = value.toString()
}

class BigIntForm(val value: BigInteger, override val loc: SourceSection? = null) : Form() {
    override val metaObj = BigIntMeta
    override fun copy() = BigIntForm(value, loc)
    override fun toString(): String = "${value}N"
}

class BigDecForm(val value: BigDecimal, override val loc: SourceSection? = null) : Form() {
    override val metaObj = BigDecMeta
    override fun copy() = BigDecForm(value, loc)
    override fun toString(): String = "${value}M"
}

class StringForm(val value: String, override val loc: SourceSection? = null) : Form() {
    override val metaObj = StringMeta
    override fun copy() = StringForm(value, loc)
    override fun toString(): String = "\"${value.reescape()}\""
}

class SymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = SymbolFormMeta
    override fun copy() = SymbolForm(sym, loc)
    override fun toString(): String = sym.name
}

class QSymbolForm(val ns: Symbol, val member: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = QSymbolFormMeta
    override fun copy() = QSymbolForm(ns, member, loc)
    override fun toString(): String = "${ns.name}/${member.name}"
}

class KeywordForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = KeywordFormMeta
    override fun copy() = KeywordForm(sym, loc)
    override fun toString(): String = ":${sym.name}"
}

class QKeywordForm(val ns: Symbol, val member: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = QKeywordFormMeta
    override fun copy() = QKeywordForm(ns, member, loc)
    override fun toString(): String = ":${ns.name}/${member.name}"
}

class DotSymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = DotSymbolFormMeta
    override fun copy() = DotSymbolForm(sym, loc)
    override fun toString(): String = ".${sym.name}"
}

class QDotSymbolForm(val ns: Symbol, val member: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = QDotSymbolFormMeta
    override fun copy() = QDotSymbolForm(ns, member, loc)
    override fun toString(): String = "${ns.name}/.${member.name}"
}

@ExportLibrary(InteropLibrary::class)
class ListForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = ListMeta
    override fun copy() = ListForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "(", separator = " ", postfix = ")")

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els)
    }
}

@ExportLibrary(InteropLibrary::class)
class VectorForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = VectorMeta
    override fun copy() = VectorForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els)
    }
}

@ExportLibrary(InteropLibrary::class)
class SetForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = SetMeta
    override fun copy() = SetForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els)
    }
}

@ExportLibrary(InteropLibrary::class)
class RecordForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = RecordMeta
    override fun copy() = RecordForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 1L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any {
        if (idx != 0L) throw InvalidArrayIndexException.create(idx)
        return BridjeVector(els)
    }
}

