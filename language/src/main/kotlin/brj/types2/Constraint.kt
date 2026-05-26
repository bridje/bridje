package brj.types2

import java.util.LinkedList
import java.util.Queue

data class Constraint(val sub: Type, val sup: Type)

class TypeErrorException(message: String) : Exception(message)

internal data class Substs(val base: BaseSubst, val nullability: NullabilitySubst)

internal fun BaseType.applySubst(substs: Substs): BaseType = when (this) {
    is TypeVarType -> {
        val bound = substs.base[tv]
        if (bound == null) this
        else if (bound is TypeVarType && bound.tv === tv) bound
        else bound.applySubst(substs)
    }
    is FnType -> FnType(params.map { it.applySubst(substs) }, ret.applySubst(substs))
    else -> this
}

internal fun Nullability.applySubst(subst: NullabilitySubst): Nullability {
    val tv = nv ?: return this
    val bound = subst[tv] ?: return this
    if (bound.nv === tv) return bound
    return bound.applySubst(subst)
}

internal fun Type.applySubst(substs: Substs): Type =
    Type(base.applySubst(substs), nullability.applySubst(substs.nullability))

private fun resolveBase(sub: BaseType, sup: BaseType, substs: Substs, queue: Queue<Constraint>): Substs {
    val subResolved = sub.applySubst(substs)
    val supResolved = sup.applySubst(substs)

    return when {
        subResolved is TypeVarType && supResolved is TypeVarType && subResolved.tv === supResolved.tv ->
            substs

        subResolved is TypeVarType ->
            substs.copy(base = substs.base + (subResolved.tv to supResolved))

        supResolved is TypeVarType ->
            substs.copy(base = substs.base + (supResolved.tv to subResolved))

        subResolved is FnType && supResolved is FnType -> {
            if (subResolved.params.size != supResolved.params.size) {
                throw TypeErrorException(
                    "Function arity mismatch: ${subResolved.params.size} vs ${supResolved.params.size}"
                )
            }
            subResolved.params.zip(supResolved.params).forEach { (pSub, pSup) ->
                queue.add(Constraint(pSup, pSub))
            }
            queue.add(Constraint(subResolved.ret, supResolved.ret))
            substs
        }

        subResolved == supResolved -> substs

        else -> throw TypeErrorException(
            "Incompatible base types: $subResolved is not a subtype of $supResolved"
        )
    }
}

private fun resolveNullability(sub: Nullability, sup: Nullability, substs: Substs): Substs {
    val subResolved = sub.applySubst(substs.nullability)
    val supResolved = sup.applySubst(substs.nullability)

    return when {
        subResolved.nv != null && supResolved.nv != null && subResolved.nv === supResolved.nv ->
            substs

        subResolved.nv != null ->
            substs.copy(nullability = substs.nullability + (subResolved.nv to supResolved))

        supResolved.nv != null ->
            substs.copy(nullability = substs.nullability + (supResolved.nv to subResolved))

        subResolved.value == supResolved.value -> substs

        else -> throw TypeErrorException(
            "Incompatible nullabilities: ${subResolved.value} is not a subtype of ${supResolved.value}"
        )
    }
}

internal fun Collection<Constraint>.resolve(): Substs {
    val queue: Queue<Constraint> = LinkedList(this)
    var substs = Substs(emptyMap(), emptyMap())

    while (queue.isNotEmpty()) {
        val (sub, sup) = queue.poll()
        substs = resolveBase(sub.base, sup.base, substs, queue)
        substs = resolveNullability(sub.nullability, sup.nullability, substs)
    }

    return substs
}
