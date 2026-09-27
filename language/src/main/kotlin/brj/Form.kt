package brj

import brj.runtime.BridjeRecord
import brj.runtime.BridjeVector
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BuiltinMetaObj
import brj.runtime.LOC_KEY
import brj.runtime.Loc
import brj.runtime.Meta
import brj.runtime.QSymbol
import brj.runtime.Symbol
import brj.runtime.TagConstructor
import brj.runtime.Tagged
import brj.runtime.sym
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString
import java.math.BigDecimal
import java.math.BigInteger

private val RDR_NS = "brj.rdr".sym

val FORM_SYM_KEY = QSymbol(RDR_NS, "sym".sym)
val FORM_NS_KEY = QSymbol(RDR_NS, "ns".sym)
val FORM_MEMBER_KEY = QSymbol(RDR_NS, "member".sym)
val FORM_ELS_KEY = QSymbol(RDR_NS, "els".sym)
val FORM_VALUE_KEY = QSymbol(RDR_NS, "value".sym)

val FORM_KEYS = listOf(FORM_SYM_KEY, FORM_NS_KEY, FORM_MEMBER_KEY, FORM_ELS_KEY, FORM_VALUE_KEY)

/**
 * A form's tag in `brj.rdr`, constructed from a record carrying [keys]: `rdr/List{.els}`.
 */
abstract class FormMeta(tagName: String, override val keys: List<QSymbol>) :
    BuiltinMetaObj(tagName.sym, RDR_NS), TagConstructor {

    abstract fun construct(payload: BridjeRecord): Form

    @Throws(ArityException::class)
    @TruffleBoundary
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val record = arguments[0] as? BridjeRecord
            ?: throw incorrect("rdr/${tagName.name} expects a record, got ${arguments[0]}")
        val missing = keys.filterNot { record.hasKey(it) }
        if (missing.isNotEmpty())
            throw incorrect("rdr/${tagName.name} requires ${missing.joinToString(", ") { it.toDisplayString() }}")
        return construct(record)
    }
}

private fun formPayload(vararg entries: Pair<QSymbol, Any>): BridjeRecord =
    BridjeRecord(Array(entries.size) { entries[it].first }, entries.map { it.second })

object SymbolFormMeta : FormMeta("SymbolForm", listOf(FORM_SYM_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is SymbolForm
    override fun construct(payload: BridjeRecord) = SymbolForm(payload[FORM_SYM_KEY] as Symbol)
}

object QSymbolFormMeta : FormMeta("QSymbolForm", listOf(FORM_NS_KEY, FORM_MEMBER_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is QSymbolForm
    override fun construct(payload: BridjeRecord) =
        QSymbolForm(payload[FORM_NS_KEY] as Symbol, payload[FORM_MEMBER_KEY] as Symbol)
}

object DotSymbolFormMeta : FormMeta("DotSymbolForm", listOf(FORM_SYM_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is DotSymbolForm
    override fun construct(payload: BridjeRecord) = DotSymbolForm(payload[FORM_SYM_KEY] as Symbol)
}

object QDotSymbolFormMeta : FormMeta("QDotSymbolForm", listOf(FORM_NS_KEY, FORM_MEMBER_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is QDotSymbolForm
    override fun construct(payload: BridjeRecord) =
        QDotSymbolForm(payload[FORM_NS_KEY] as Symbol, payload[FORM_MEMBER_KEY] as Symbol)
}

object ListMeta : FormMeta("List", listOf(FORM_ELS_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is ListForm
    override fun construct(payload: BridjeRecord) = ListForm((payload[FORM_ELS_KEY] as BridjeVector).toFormList())
}

object VectorMeta : FormMeta("Vector", listOf(FORM_ELS_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is VectorForm
    override fun construct(payload: BridjeRecord) = VectorForm((payload[FORM_ELS_KEY] as BridjeVector).toFormList())
}

object RecordMeta : FormMeta("Record", listOf(FORM_ELS_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is RecordForm
    override fun construct(payload: BridjeRecord) = RecordForm((payload[FORM_ELS_KEY] as BridjeVector).toFormList())
}

object SetMeta : FormMeta("Set", listOf(FORM_ELS_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is SetForm
    override fun construct(payload: BridjeRecord) = SetForm((payload[FORM_ELS_KEY] as BridjeVector).toFormList())
}

object IntMeta : FormMeta("Int", listOf(FORM_VALUE_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is IntForm
    override fun construct(payload: BridjeRecord) = IntForm(payload[FORM_VALUE_KEY] as Long)
}

object DoubleMeta : FormMeta("Double", listOf(FORM_VALUE_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is DoubleForm
    override fun construct(payload: BridjeRecord) = DoubleForm(payload[FORM_VALUE_KEY] as Double)
}

object StringMeta : FormMeta("String", listOf(FORM_VALUE_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is StringForm
    override fun construct(payload: BridjeRecord) =
        StringForm((payload[FORM_VALUE_KEY] as TruffleString).toJavaStringUncached())
}

object BigIntMeta : FormMeta("BigInt", listOf(FORM_VALUE_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is BigIntForm
    override fun construct(payload: BridjeRecord) = BigIntForm(payload[FORM_VALUE_KEY] as BigInteger)
}

object BigDecMeta : FormMeta("BigDec", listOf(FORM_VALUE_KEY)) {
    override fun isMetaInstance(instance: Any?) = instance is BigDecForm
    override fun construct(payload: BridjeRecord) = BigDecForm(payload[FORM_VALUE_KEY] as BigDecimal)
}

@ExportLibrary(InteropLibrary::class)
sealed class Form : TruffleObject, Meta<Form>, Tagged {
    abstract val loc: SourceSection?
    abstract val metaObj: FormMeta

    var staticMeta: RecordForm? = null
        protected set

    private var _meta: BridjeRecord? = null

    override val meta: BridjeRecord
        get() = _meta ?: loc?.let { BridjeRecord.EMPTY.put(LOC_KEY, Loc(it)) } ?: BridjeRecord.EMPTY

    abstract fun copy(): Form

    fun withStaticMeta(member: DotSymbolForm): Form =
        withStaticMeta(RecordForm(listOf(member, SymbolForm("true".sym)), member.loc))

    fun withStaticMeta(member: QDotSymbolForm): Form =
        withStaticMeta(RecordForm(listOf(member, SymbolForm("true".sym)), member.loc))

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

    @ExportMessage fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean): Any = INTEROP.getMembers(payload, includeInternal)

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(member: String): Boolean = INTEROP.isMemberReadable(payload, member)

    @ExportMessage
    @TruffleBoundary
    @Throws(UnknownIdentifierException::class)
    fun readMember(member: String): Any = INTEROP.readMember(payload, member)

    private companion object {
        private val INTEROP = InteropLibrary.getUncached()
    }

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
    override val payload: BridjeRecord by lazy { formPayload(FORM_VALUE_KEY to value) }
    override fun toString(): String = value.toString()
}

class DoubleForm(val value: Double, override val loc: SourceSection? = null) : Form() {
    override val metaObj = DoubleMeta
    override fun copy() = DoubleForm(value, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_VALUE_KEY to value) }
    override fun toString(): String = value.toString()
}

class BigIntForm(val value: BigInteger, override val loc: SourceSection? = null) : Form() {
    override val metaObj = BigIntMeta
    override fun copy() = BigIntForm(value, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_VALUE_KEY to value) }
    override fun toString(): String = "${value}N"
}

class BigDecForm(val value: BigDecimal, override val loc: SourceSection? = null) : Form() {
    override val metaObj = BigDecMeta
    override fun copy() = BigDecForm(value, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_VALUE_KEY to value) }
    override fun toString(): String = "${value}M"
}

class StringForm(val value: String, override val loc: SourceSection? = null) : Form() {
    override val metaObj = StringMeta
    override fun copy() = StringForm(value, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_VALUE_KEY to TruffleString.fromJavaStringUncached(value, TruffleString.Encoding.UTF_8)) }
    override fun toString(): String = "\"${value.reescape()}\""
}

class SymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = SymbolFormMeta
    override fun copy() = SymbolForm(sym, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_SYM_KEY to sym) }
    override fun toString(): String = sym.name
}

class QSymbolForm(val ns: Symbol, val member: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = QSymbolFormMeta
    override fun copy() = QSymbolForm(ns, member, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_NS_KEY to ns, FORM_MEMBER_KEY to member) }
    override fun toString(): String = "${ns.name}/${member.name}"
}

class DotSymbolForm(val sym: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = DotSymbolFormMeta
    override fun copy() = DotSymbolForm(sym, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_SYM_KEY to sym) }
    override fun toString(): String = ".${sym.name}"
}

class QDotSymbolForm(val ns: Symbol, val member: Symbol, override val loc: SourceSection? = null) : Form() {
    override val metaObj = QDotSymbolFormMeta
    override fun copy() = QDotSymbolForm(ns, member, loc)
    override val payload: BridjeRecord by lazy { formPayload(FORM_NS_KEY to ns, FORM_MEMBER_KEY to member) }
    override fun toString(): String = "${ns.name}/.${member.name}"
}

class ListForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = ListMeta
    override fun copy() = ListForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "(", separator = " ", postfix = ")")
    override val payload: BridjeRecord by lazy { formPayload(FORM_ELS_KEY to BridjeVector(els)) }
}

class VectorForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = VectorMeta
    override fun copy() = VectorForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")
    override val payload: BridjeRecord by lazy { formPayload(FORM_ELS_KEY to BridjeVector(els)) }
}

class SetForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = SetMeta
    override fun copy() = SetForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")
    override val payload: BridjeRecord by lazy { formPayload(FORM_ELS_KEY to BridjeVector(els)) }
}

class RecordForm(val els: List<Form>, override val loc: SourceSection? = null) : Form() {
    override val metaObj = RecordMeta
    override fun copy() = RecordForm(els, loc)
    override fun toString(): String = els.joinToString(prefix = "{", separator = " ", postfix = "}")
    override val payload: BridjeRecord by lazy { formPayload(FORM_ELS_KEY to BridjeVector(els)) }
}

