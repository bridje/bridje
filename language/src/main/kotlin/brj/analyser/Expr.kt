package brj.analyser

import brj.Form
import brj.GlobalVar
import brj.types.Type
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
    val typeVarNames: List<String> = emptyList(),
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String =
        if (fieldNames.isEmpty()) "(tag $name)"
        else "(tag ($name ${fieldNames.joinToString(" ")}))"
}

class DefMacroExpr(
    val name: String,
    val fn: FnExpr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defmacro $name ${fn.params.joinToString(" ") { it.name }} ${fn.bodyExpr})"
}

class DefKeysExpr(
    val names: List<String>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(decl ${names.joinToString(" ") { ".$it" }})"
}

class DeclExpr(
    val name: String,
    val declaredType: Type,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(decl $name $declaredType)"
}

class DefxExpr(
    val name: String,
    val declaredType: Type,
    val defaultExpr: ValueExpr?,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defx $name $declaredType${defaultExpr?.let { " $it" } ?: ""})"
}

enum class InteropMemberKind { STATIC_FIELD, STATIC_METHOD, INSTANCE_METHOD, INSTANCE_FIELD }

data class InteropMember(
    val qualifiedName: String,
    val importAlias: String,
    val memberName: String,
    val kind: InteropMemberKind,
    val declaredType: Type,
)

class InteropDeclExpr(
    val members: List<InteropMember>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(interop-decl ${members.joinToString(" ") { it.qualifiedName }})"
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
