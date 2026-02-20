package brj.analyser

import brj.*

import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.SourceSection
import java.math.BigDecimal
import java.math.BigInteger

sealed interface ValueExpr : Expr

class IntExpr(val value: Long, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = value.toString()
}

class DoubleExpr(val value: Double, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = value.toString()
}

class BigIntExpr(val value: BigInteger, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = "${value}N"
}

class BigDecExpr(val value: BigDecimal, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = "${value}M"
}

class StringExpr(val value: String, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = "\"${value.reescape()}\""
}

class VectorExpr(val els: List<ValueExpr>, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = els.joinToString(prefix = "[", separator = " ", postfix = "]")
}

class SetExpr(val els: List<ValueExpr>, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = els.joinToString(prefix = "#{", separator = " ", postfix = "}")
}

class RecordExpr(
    val fields: List<Pair<String, ValueExpr>>,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String =
        fields.joinToString(prefix = "{", separator = ", ", postfix = "}") { (k, v) -> "$k $v" }
}

class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = localVar.name
}

class LetExpr(
    val localVar: LocalVar,
    val bindingExpr: ValueExpr,
    val bodyExpr: ValueExpr,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "(let [${localVar.name} $bindingExpr] $bodyExpr)"
}

class FnExpr(
    val fnName: String,
    val params: List<String>,
    val bodyExpr: ValueExpr,
    val slotCount: Int,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "(fn ($fnName ${params.joinToString(" ")}) $bodyExpr)"
}

class CallExpr(
    val fnExpr: ValueExpr,
    val argExprs: List<ValueExpr>,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "($fnExpr ${argExprs.joinToString(" ")})"
}

class DoExpr(
    val sideEffects: List<ValueExpr>,
    val result: ValueExpr,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String {
        val all = sideEffects + result
        return "(do ${all.joinToString(" ")})"
    }
}

class BoolExpr(val value: Boolean, override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = value.toString()
}

class NilExpr(override val loc: SourceSection? = null) : ValueExpr {
    override fun toString(): String = "nil"
}

class IfExpr(
    val predExpr: ValueExpr,
    val thenExpr: ValueExpr,
    val elseExpr: ValueExpr,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "(if $predExpr $thenExpr $elseExpr)"
}

class GlobalVarExpr(
    val globalVar: GlobalVar,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = globalVar.name
}

class TruffleObjectExpr(
    val value: TruffleObject,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = value.toString()
}

class HostStaticMethodExpr(
    val hostClass: TruffleObject,
    val methodName: String,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "$hostClass/$methodName"
}

class HostConstructorExpr(
    val hostClass: TruffleObject,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "$hostClass/new"
}

class QuoteExpr(
    val form: Form,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "'$form"
}

sealed class CasePattern {
    abstract val loc: SourceSection?
}

class TagPattern(
    val tagValue: Any,
    val bindings: List<LocalVar>,
    override val loc: SourceSection? = null
) : CasePattern() {
    override fun toString(): String =
        if (bindings.isEmpty()) "$tagValue"
        else "$tagValue(${bindings.joinToString(", ") { it.name }})"
}

class DefaultPattern(override val loc: SourceSection? = null) : CasePattern() {
    override fun toString(): String = "_"
}

class NilPattern(override val loc: SourceSection? = null) : CasePattern() {
    override fun toString(): String = "nil"
}

class CatchAllBindingPattern(
    val binding: LocalVar,
    override val loc: SourceSection? = null
) : CasePattern() {
    override fun toString(): String = binding.name
}

class CaseBranch(
    val pattern: CasePattern,
    val bodyExpr: ValueExpr,
    val loc: SourceSection? = null
) {
    override fun toString(): String = "$pattern $bodyExpr"
}

class CaseExpr(
    val scrutinee: ValueExpr,
    val branches: List<CaseBranch>,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String =
        "(case $scrutinee ${branches.joinToString(" ")})"
}

class RecordSetExpr(
    val recordExpr: ValueExpr,
    val key: String,
    val valueExpr: ValueExpr,
    override val loc: SourceSection? = null
) : ValueExpr {
    override fun toString(): String = "(set! $recordExpr :$key $valueExpr)"
}

class ErrorValueExpr(
    val message: String,
    override val loc: SourceSection? = null,
) : ValueExpr {
    override fun toString(): String = "<error: $message>"
}
