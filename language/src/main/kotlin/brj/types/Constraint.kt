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
                        if (lower.base.paramTypes.size != upper.base.paramTypes.size)
                            throw TypeErrorException("Function arity mismatch: ${lower.base.paramTypes.size} vs ${upper.base.paramTypes.size}")
                        // params contravariant, return covariant
                        upper.base.paramTypes.zip(lower.base.paramTypes).forEach { (u, l) -> queue.add(u subOf l) }
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
