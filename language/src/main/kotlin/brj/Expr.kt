package brj

import brj.runtime.DefxVar
import brj.runtime.GlobalVar
import brj.runtime.Symbol
import com.oracle.truffle.api.source.SourceSection
import java.util.*
import javax.management.ValueExp

internal sealed class Expr {
    abstract val loc: SourceSection?
}

internal sealed class ValueExpr : Expr()

internal class IntExpr(val int: Int, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is IntExpr && int == other.int)

    override fun hashCode() = Objects.hash(int)
}

internal class BoolExpr(val bool: Boolean, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is BoolExpr && bool == other.bool)

    override fun hashCode() = Objects.hash(bool)
}

internal class StringExpr(val string: String, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is StringExpr && string == other.string)

    override fun hashCode() = Objects.hash(string)

    override fun toString() = "\"$string\""
}

internal class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is VectorExpr && exprs == other.exprs)

    override fun hashCode() = Objects.hash(exprs)
}


internal class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is SetExpr && exprs == other.exprs)

    override fun hashCode() = Objects.hash(exprs)
}

internal class RecordExpr(val entries: Map<Symbol, ValueExpr>, override val loc: SourceSection?): ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is RecordExpr && entries == other.entries)

    override fun hashCode() = Objects.hash(entries)
}

internal class KeywordExpr(val sym: Symbol, override val loc: SourceSection?): ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is KeywordExpr && sym == other.sym)

    override fun hashCode() = Objects.hash(sym)
}

internal class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other || (other is DoExpr && exprs == other.exprs && expr == other.expr)

    override fun hashCode() = Objects.hash(exprs, expr)
}

internal class IfExpr(
    val predExpr: ValueExpr,
    val thenExpr: ValueExpr,
    val elseExpr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) =
        this === other ||
                (other is IfExpr
                        && predExpr == other.predExpr
                        && thenExpr == other.thenExpr
                        && elseExpr == other.elseExpr)

    override fun hashCode() = Objects.hash(predExpr, thenExpr, elseExpr)
}

internal class Binding(val binding: LocalVar, val expr: ValueExpr) {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Binding -> false
        else -> binding == other.binding && expr == other.expr
    }

    override fun hashCode() = Objects.hash(binding, expr)
}

internal class LetExpr(
    val bindings: List<Binding>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is LetExpr -> false
        else -> bindings == other.bindings && expr == other.expr
    }

    override fun hashCode(): Int = Objects.hash(bindings, expr)
}

internal class LoopExpr(
    val bindings: List<Binding>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is LoopExpr -> false
        else -> bindings == other.bindings && expr == other.expr
    }

    override fun hashCode(): Int = Objects.hash(bindings, expr)
}

internal class RecurExpr(
    val exprs: List<Binding>,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is RecurExpr -> false
        else -> exprs == other.exprs
    }

    override fun hashCode() = Objects.hash(exprs)
}

internal class FnExpr(
    val fxLocal: LocalVar,
    val params: List<LocalVar>,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is FnExpr -> false
        else -> params == other.params && fxLocal == other.fxLocal && expr == other.expr
    }

    override fun hashCode() = Objects.hash(params, fxLocal, expr)
}

internal class CallExpr(
    val fn: ValueExpr,
    val fxExpr: ValueExpr,
    val args: List<ValueExpr>,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is CallExpr -> false
        else -> fn == other.fn && fxExpr == other.fxExpr && args == other.args
    }

    override fun hashCode() = Objects.hash(fn, fxExpr, args)
}

internal data class WithFxBinding(val defxVar: DefxVar, val expr: ValueExpr)

internal class WithFxExpr(
    val oldFx: LocalVar,
    val bindings: List<WithFxBinding>,
    val newFx: LocalVar,
    val expr: ValueExpr,
    override val loc: SourceSection?
) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is WithFxExpr -> false
        else -> bindings == other.bindings && oldFx == other.oldFx && newFx == other.newFx && expr == other.expr
    }

    override fun hashCode() = Objects.hash(bindings, oldFx, newFx, expr)
}

internal class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LocalVarExpr -> false
        else -> localVar == other.localVar
    }

    override fun hashCode() = Objects.hash(localVar)
}

internal class GlobalVarExpr(val globalVar: GlobalVar, override val loc: SourceSection?) : ValueExpr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is GlobalVarExpr -> false
        else -> globalVar == other.globalVar
    }

    override fun hashCode() = Objects.hash(globalVar)
}

internal class DefExpr(val sym: Symbol, val expr: ValueExpr, override val loc: SourceSection?) : Expr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DefExpr -> false
        else -> sym == other.sym && expr == other.expr
    }

    override fun hashCode() = Objects.hash(sym, expr)
}

internal class DefxExpr(val sym: Symbol, val typing: Typing, override val loc: SourceSection?) : Expr() {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DefxExpr -> false
        else -> sym == other.sym
    }

    override fun hashCode() = Objects.hash(sym)
}
