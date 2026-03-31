package brj.types

import brj.types.Nullability.*
import java.util.LinkedList
import java.util.Queue

internal data class Constraint(val lower: Type, val upper: Type)

internal infix fun Type.subOf(upper: Type) = Constraint(this, upper)

internal fun Collection<Constraint>.resolve(): Subst {
    val queue: Queue<Constraint> = LinkedList(this)
    var subst: Subst = emptyMap()

    while (queue.isNotEmpty()) {
        val (lower, upper) = queue.poll()

        // Handle base types
        when {
            lower.base == null && upper.base == null -> {
                // Both unknown - still propagate nullability info
                subst = subst.plusLower(upper.tv, lower)
            }

            lower.base != null && upper.base == null -> {
                // lower flows into upper - join lower into upper's binding
                subst = subst.plusLower(upper.tv, lower)
            }

            lower.base == null && upper.base != null -> {
                // upper constrains lower - meet upper into lower's binding
                subst = subst.plusUpper(lower.tv, upper)
            }

            lower.base != null && upper.base != null -> {
                // Both have bases - must be compatible
                when {
                    lower.base == upper.base -> { /* ok */ }

                    lower.base is VectorType && upper.base is VectorType -> {
                        queue.add(lower.base.el subOf upper.base.el)
                    }

                    lower.base is FnType && upper.base is FnType -> {
                        val lParams = lower.base.paramTypes
                        val uParams = upper.base.paramTypes

                        val commonParams = when {
                            lParams.size == uParams.size -> lParams.size
                            lParams.size == uParams.size + 1 && lParams.last().base is RecordType -> uParams.size
                            uParams.size == lParams.size + 1 && uParams.last().base is RecordType -> lParams.size
                            else -> throw TypeErrorException("Function arity mismatch: ${lParams.size} vs ${uParams.size}")
                        }

                        // params contravariant, return covariant
                        (0 until commonParams).forEach { i -> queue.add(uParams[i] subOf lParams[i]) }
                        queue.add(lower.base.returnType subOf upper.base.returnType)
                    }

                    else -> throw TypeErrorException(
                        "Incompatible types: ${lower.base} is not a subtype of ${upper.base}"
                    )
                }
            }
        }

        // Check nullability compatibility
        if (lower.nullability == NULLABLE && upper.nullability == NOT_NULL) {
            throw TypeErrorException("Cannot pass nullable to non-null context")
        }
    }

    return subst
}
