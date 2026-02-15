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
    val metaExpr: ValueExpr? = null,
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
    override fun toString(): String = "(defkey $name)"
}

class TopLevelDo(val forms: List<Form>, override val loc: SourceSection?) : Expr {
    override fun toString(): String = 
        forms.joinToString(prefix = "(do ", separator = " ", postfix = ")")
}

class AnalyserErrors(
    override val loc: SourceSection?, 
    val errors: List<Analyser.Error>,
): Expr {
    override fun toString() = "ERRORS"
}
