package brj.types

import brj.types.Nullability.*
import java.util.LinkedList
import java.util.Queue

internal data class Constraint(val lower: Type, val upper: Type)

internal infix fun Type.subOf(upper: Type) = Constraint(this, upper)

// Java interfaces backing the virtual protocol types.
// See #78: HostType <: IterableType rewrites to HostType <: HostType(j.l.Iterable)
// and lets existing class hierarchy subtyping handle the rest.
private val J_L_ITERABLE = Iterable::class.java
private val J_U_ITERATOR = Iterator::class.java

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

                    // Collection types — covariant in element
                    lower.base is VectorType && upper.base is VectorType -> {
                        queue.add(lower.base.el subOf upper.base.el)
                    }
                    lower.base is SetType && upper.base is SetType -> {
                        queue.add(lower.base.el subOf upper.base.el)
                    }

                    lower.base is HostType && upper.base is HostType
                        && lower.base.className == upper.base.className
                        && lower.base.args.size == upper.base.args.size
                        && lower.base.args.isNotEmpty() -> {
                        lower.base.variances.zip(lower.base.args.zip(upper.base.args)).forEach { (variance, args) ->
                            val (lArg, uArg) = args
                            when (variance) {
                                Variance.OUT -> queue.add(lArg subOf uArg)
                                Variance.IN -> queue.add(uArg subOf lArg)
                                Variance.INVARIANT -> { queue.add(lArg subOf uArg); queue.add(uArg subOf lArg) }
                            }
                        }
                    }

                    // HostType with no args is the erased form — compatible if class names match
                    lower.base is HostType && upper.base is HostType
                        && lower.base.className == upper.base.className -> { /* ok — erased */ }

                    // HostType subtyping across different classes via Java class hierarchy
                    lower.base is HostType && upper.base is HostType
                        && lower.base.className != upper.base.className -> {
                        val mappedArgs = HostTypeHierarchy.findSupertypeArgs(
                            lower.base.className, upper.base.className, lower.base.args
                        ) ?: throw TypeErrorException(
                            "Incompatible types: ${lower.base} is not a subtype of ${upper.base}"
                        )
                        if (mappedArgs.isNotEmpty() && upper.base.args.isNotEmpty()) {
                            upper.base.variances.zip(mappedArgs.zip(upper.base.args)).forEach { (variance, args) ->
                                val (lArg, uArg) = args
                                when (variance) {
                                    Variance.OUT -> queue.add(lArg subOf uArg)
                                    Variance.IN -> queue.add(uArg subOf lArg)
                                    Variance.INVARIANT -> { queue.add(lArg subOf uArg); queue.add(uArg subOf lArg) }
                                }
                            }
                        }
                    }

                    lower.base is TagType && upper.base is TagType
                        && lower.base.ns == upper.base.ns
                        && lower.base.name == upper.base.name
                        && lower.base.args.size == upper.base.args.size -> {
                        lower.base.variances.zip(lower.base.args.zip(upper.base.args)).forEach { (variance, args) ->
                            val (lArg, uArg) = args
                            when (variance) {
                                Variance.OUT -> queue.add(lArg subOf uArg)
                                Variance.IN -> queue.add(uArg subOf lArg)
                                Variance.INVARIANT -> { queue.add(lArg subOf uArg); queue.add(uArg subOf lArg) }
                            }
                        }
                    }

                    lower.base is EnumType && upper.base is EnumType
                        && lower.base.name == upper.base.name
                        && lower.base.args.size == upper.base.args.size
                        && lower.base.args.isNotEmpty() -> {
                        lower.base.variances.zip(lower.base.args.zip(upper.base.args)).forEach { (variance, args) ->
                            val (lArg, uArg) = args
                            when (variance) {
                                Variance.OUT -> queue.add(lArg subOf uArg)
                                Variance.IN -> queue.add(uArg subOf lArg)
                                Variance.INVARIANT -> { queue.add(lArg subOf uArg); queue.add(uArg subOf lArg) }
                            }
                        }
                    }

                    // Protocol types with themselves — covariant in el
                    lower.base is IterableType && upper.base is IterableType ->
                        queue.add(lower.base.el subOf upper.base.el)
                    lower.base is IteratorType && upper.base is IteratorType ->
                        queue.add(lower.base.el subOf upper.base.el)

                    // VectorType <: IterableType — direct, covariant
                    lower.base is VectorType && upper.base is IterableType ->
                        queue.add(lower.base.el subOf upper.base.el)

                    // HostType <: protocol type — rewrite to HostType <: HostType(java interface)
                    lower.base is HostType && upper.base is IterableType ->
                        queue.add(lower subOf HostType(J_L_ITERABLE.name, listOf(upper.base.el), listOf(Variance.OUT)).notNull())
                    lower.base is HostType && upper.base is IteratorType ->
                        queue.add(lower subOf HostType(J_U_ITERATOR.name, listOf(upper.base.el), listOf(Variance.OUT)).notNull())

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
