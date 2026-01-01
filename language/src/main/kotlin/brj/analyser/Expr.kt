package brj.analyser

import brj.Form

import com.oracle.truffle.api.source.SourceSection

sealed interface Expr {
    val loc: SourceSection?
    override fun toString(): String
}

class DefExpr(
    val name: String,
    val valueExpr: ValueExpr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(def $name $valueExpr)"
}

class DefTagExpr(
    val name: String,
    val fieldNames: List<String>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String =
        if (fieldNames.isEmpty()) "(deftag $name)"
        else "(deftag ($name ${fieldNames.joinToString(" ")}))"
}

class DefMacroExpr(
    val name: String,
    val fn: FnExpr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defmacro $name ${fn.params.joinToString(" ")} ${fn.bodyExpr})"
}

class DefKeyExpr(
    val name: String,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defkey :$name)"
}

sealed class TopLevelDoOrExpr

class TopLevelDo(val forms: List<Form>) : TopLevelDoOrExpr()

class TopLevelExpr(val expr: Expr) : TopLevelDoOrExpr()
