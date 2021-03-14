package brj

import brj.runtime.Symbol

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

/*
is there a case, in Bridje, where we'd want a node to flow to two other nodes?
or, in the cases where Dolan ends up with two outgoing flow edges, should we be merging the nodes?

`(do (foo x) (identity x))`
`(: (foo {:foo Int}) Int)`
fine - because x would take on the type of foo. ah, but we'd want the return type to be whatever the type of x is
so x has to be a subtype of whatever the foo fn expects, but would return that.
let's assume foo takes {:foo Int}, the expected type here would be a ^ {:foo Int} -> a

ok, so, unification

let's say we were to do this with bounds instead.
we'd get to a point where we'd know that the variable is a record, we then need to know what it contains
seems like we still need polarity (roughly) because a variable can have more fields added to it, a literal record can't

-- more thoughts (2021-01-27)

so x is an input, it's a negative type
it's passed to something that requires a certain type
it's then passed to something else that requires another type
x is then the intersection of those two types. fine.

we have a literal map, which is then assigned to x
x is then passed to something which expects a key that x doesn't have (:foo)
we're compositional, so this will arise when the binding for x meets the usage
the usage will specify that x is required to have key :foo, the binding will specify that it doesn't have it.
'let', then, needs to be special - it knows that its binding is a positive type
not all positive types are restricted, though, like literal maps.
x could just be bound to y, which might be an input to the function.

constraints?
we can say that a < {:foo Int} etc, in a -> a - this is equivalent to a & {:foo Int} -> a
saying a -> a & {:foo Int} doesn't work under usual polarity rules, because when we then say that a & {:foo Int} < b,
this is saying that _either_ a or {:foo Int} is < b, which is a pain.
in practice, we now know that a has to be a record.
do we need to introduce a 'cons' type? but this is essentially standard record polymorphism

*/

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
    is VectorExpr -> collTyping(::VectorType, expr.exprs)
    is SetExpr -> collTyping(::SetType, expr.exprs)
    is DoExpr -> doTyping(expr)
    is IfExpr -> ifTyping(expr)
    is LocalVarExpr -> localVarExprTyping(expr.localVar)
    else -> TODO()
}
