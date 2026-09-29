package brj.analyser

import brj.Form
import brj.GlobalVar
import brj.runtime.Symbol
import brj.types.Type
import brj.types.TypeVar
import com.oracle.truffle.api.source.SourceSection

sealed interface Expr {
    val loc: SourceSection?
    override fun toString(): String
}

class DefExpr(
    val name: Symbol,
    val valueExpr: ValueExpr,
    val metaExpr: ValueExpr? = null,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(def $name $valueExpr)"
}

class DefTagExpr(
    val name: Symbol,
    val keys: List<Symbol>?,
    val typeVarNames: List<String> = emptyList(),
    override val loc: SourceSection? = null,
    val typeVars: List<TypeVar> = emptyList(),
    // `.value(a)`: the types, in terms of [typeVars], that the key's declared type is instantiated at.
    val keyArgs: Map<Symbol, List<Type>> = emptyMap(),
) : Expr {
    override fun toString(): String =
        if (keys == null) "(tag $name)"
        else "(tag $name{${keys.joinToString(", ") { ".${it.name}" }}})"
}

class DefEnumExpr(
    val name: Symbol,
    val typeVarNames: List<String>,
    val variants: List<DefTagExpr>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String =
        "(enum $name ${variants.joinToString(" ")})"
}

class DefMacroExpr(
    val name: Symbol,
    val fn: FnExpr,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defmacro $name ${fn.params.joinToString(" ") { "${it.name}" }} ${fn.bodyExpr})"
}

class DefKeysExpr(
    val names: List<Symbol>,
    val types: Map<Symbol, Type> = emptyMap(),
    override val loc: SourceSection? = null,
    // `decl: [a, b] .k T`: the key's type parameters, in the order they are declared.
    val params: List<TypeVar> = emptyList(),
) : Expr {
    override fun toString(): String = "(decl ${names.joinToString(" ") { ".${it.name}" }})"
}

class DeclExpr(
    val name: Symbol,
    val declaredType: Type,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(decl $name $declaredType)"
}

class DefxExpr(
    val name: Symbol,
    val declaredType: Type,
    val defaultExpr: ValueExpr?,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(defx $name $declaredType${defaultExpr?.let { " $it" } ?: ""})"
}

enum class InteropMemberKind { STATIC_FIELD, STATIC_METHOD, INSTANCE_METHOD, INSTANCE_FIELD }

data class InteropMember(
    val importAlias: Symbol,
    val memberName: Symbol,
    val kind: InteropMemberKind,
    val declaredType: Type,
)

class InteropDeclExpr(
    val members: List<InteropMember>,
    override val loc: SourceSection? = null
) : Expr {
    override fun toString(): String = "(interop-decl ${members.joinToString(" ") { "${it.importAlias}/${it.memberName}" }})"
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
