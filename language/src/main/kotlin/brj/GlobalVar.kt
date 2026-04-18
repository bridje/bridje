package brj

import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BridjeContext
import brj.runtime.BridjeRecord
import brj.runtime.BuiltinMetaObj
import brj.runtime.Meta
import brj.runtime.Symbol
import brj.runtime.sym
import brj.types.Type
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class GlobalVar(
    val ns: Symbol,
    val name: Symbol,
    val value: Any?,
    override val meta: BridjeRecord = BridjeRecord.EMPTY,
    val type: Type? = null,
    val effects: List<GlobalVar> = emptyList(),
) : TruffleObject, Meta<GlobalVar> {

    override fun withMeta(newMeta: BridjeRecord?): GlobalVar =
        GlobalVar(ns, name, value, newMeta ?: BridjeRecord.EMPTY, type, effects)

    @ExportMessage fun hasMetaObject() = true
    @ExportMessage fun getMetaObject(): Any = VarMeta

    @ExportMessage fun hasArrayElements() = true
    @ExportMessage fun getArraySize() = 2L
    @ExportMessage fun isArrayElementReadable(idx: Long) = idx == 0L || idx == 1L

    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(idx: Long): Any = when (idx) {
        0L -> ns
        1L -> name
        else -> throw InvalidArrayIndexException.create(idx)
    }

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String = "#'${ns.name}/${name.name}"

    override fun toString(): String = "#'${ns.name}/${name.name}"
}

object VarMeta : BuiltinMetaObj("Var".sym, "brj.core".sym) {
    override fun isMetaInstance(instance: Any?) = instance is GlobalVar

    @Throws(ArityException::class)
    @TruffleBoundary
    override fun execute(arguments: Array<Any?>): Any {
        if (arguments.size != 1) throw ArityException.create(1, 1, arguments.size)
        val qsym = arguments[0] as? QSymbolForm
            ?: throw incorrect("Var: expected a qualified symbol, got ${arguments[0]?.let { it::class.simpleName }}")

        val ctx = BridjeContext.current()
        val nsEnv = ctx.namespaces[qsym.ns.name]
            ?: throw incorrect("Var: namespace not found: $qsym")
        return nsEnv[qsym.member] ?: nsEnv.effectVar(qsym.member)
            ?: throw incorrect("Var: var not found: $qsym")
    }
}
