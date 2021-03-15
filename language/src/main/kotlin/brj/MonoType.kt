package brj

internal sealed class MonoType

internal class TypeVar : MonoType() {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

internal object IntType : MonoType()
internal object BoolType : MonoType()
internal object StringType : MonoType()
internal data class VectorType(val inType: MonoType, val outType: MonoType) : MonoType()
internal data class SetType(val inType: MonoType, val outType: MonoType) : MonoType()
internal data class FnType(val paramNodes: List<MonoType>, val resNode: MonoType) : MonoType()

internal data class Typing(val res: MonoType, val lvars: Map<LocalVar, MonoType> = emptyMap()) {
    override fun toString() = res.toString()
}

internal data class Constraint(val outType: MonoType, val inType: MonoType)

private fun combineTypings(
    res: MonoType,
    typings: Iterable<Typing> = emptyList(),
    constraints: Set<Constraint> = emptySet()
): Typing {
    println("combine: $typings")
    typings
        .flatMap { it.lvars.entries }
        .groupBy({ it.key }, { it.value })
    return Typing(res)
}

private fun primitiveTyping(type: MonoType) = Typing(type)

private fun collTyping(mkType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
    val typings = exprs.map(::valueExprTyping)
    val elTypeVar = TypeVar()

    return combineTypings(mkType(elTypeVar), typings, typings.mapTo(mutableSetOf()) { Constraint(it.res, elTypeVar) })
}

private fun doTyping(doExpr: DoExpr): Typing {
    val returnExprTyping = valueExprTyping(doExpr.expr)
    return combineTypings(
        returnExprTyping.res,
        doExpr.exprs.map(::valueExprTyping) + returnExprTyping
    )
}

private fun ifTyping(ifExpr: IfExpr): Typing {
    val predTyping = valueExprTyping(ifExpr.predExpr)
    val thenTyping = valueExprTyping(ifExpr.thenExpr)
    val elseTyping = valueExprTyping(ifExpr.elseExpr)

    val resTypeVar = TypeVar()

    return combineTypings(
        resTypeVar,
        setOf(predTyping, thenTyping, elseTyping),
        setOf(
            Constraint(predTyping.res, BoolType),
            Constraint(thenTyping.res, resTypeVar),
            Constraint(elseTyping.res, resTypeVar)
        )
    )
}

private fun localVarExprTyping(localVar: LocalVar): Typing {
    val typeVar = TypeVar()
    return Typing(typeVar, mapOf(localVar to typeVar))
}

internal fun valueExprTyping(expr: ValueExpr): Typing = when (expr) {
    is IntExpr -> primitiveTyping(IntType)
    is BoolExpr -> primitiveTyping(BoolType)
    is StringExpr -> primitiveTyping(StringType)
    is DoExpr -> doTyping(expr)
    is IfExpr -> ifTyping(expr)
    is LocalVarExpr -> localVarExprTyping(expr.localVar)
    else -> TODO()
}
