package brj.types

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves Java class hierarchy relationships for HostType subtyping.
 * Caches the type parameter index mapping (not resolved types) so that
 * the reflection walk only happens once per (subClass, superClass) pair.
 */
internal object HostTypeHierarchy {

    /**
     * A cached mapping from subclass type-parameter indices to superclass type-parameter positions.
     * `null` value in the cache means "not a subtype".
     * An empty list means the supertype is raw / has no type parameters.
     * Each entry is the index into the subclass's type parameters that feeds the
     * corresponding superclass type parameter, or -1 if the superclass param is not
     * supplied by a type variable (e.g. it's a concrete type — we store -1 and
     * fall back to erased compatibility).
     */
    private data class ParamMapping(val indices: List<Int>)

    /** Sentinel for "not a subtype" since ConcurrentHashMap cannot store null values. */
    private val NOT_A_SUBTYPE = ParamMapping(listOf(Int.MIN_VALUE))

    private val cache = ConcurrentHashMap<Pair<String, String>, ParamMapping>()

    /**
     * Returns the superclass's type args derived from [subArgs], or `null` if
     * [subClassName] is not a subtype of [superClassName].
     */
    fun findSupertypeArgs(subClassName: String, superClassName: String, subArgs: List<Type>): List<Type>? {
        val mapping = cache.computeIfAbsent(subClassName to superClassName) { (sub, sup) ->
            computeMapping(sub, sup) ?: NOT_A_SUBTYPE
        }

        if (mapping === NOT_A_SUBTYPE) return null

        return if (mapping.indices.isEmpty()) {
            emptyList()
        } else {
            mapping.indices.map { idx ->
                if (idx in subArgs.indices) subArgs[idx]
                else freshType()
            }
        }
    }

    /**
     * Compute the mapping from subclass type params to superclass type params.
     * Returns null if sub is not assignable to super.
     */
    private fun computeMapping(subClassName: String, superClassName: String): ParamMapping? {
        val subClass = try {
            Class.forName(subClassName)
        } catch (_: ClassNotFoundException) {
            return null
        }
        val superClass = try {
            Class.forName(superClassName)
        } catch (_: ClassNotFoundException) {
            return null
        }

        if (!superClass.isAssignableFrom(subClass)) return null

        // Build a mapping from the subclass's TypeVariable names to their positional indices.
        val subTypeParams = subClass.typeParameters
        val subParamIndex = subTypeParams.withIndex().associate { (i, tv) -> tv.name to i }

        // BFS up the generic hierarchy to find the path to the superclass,
        // tracking how type variables map through each step.
        // We track a map from TypeVariable name -> the index in the original subclass's type params.
        val queue = LinkedList<Node>()
        val visited = mutableSetOf<Class<*>>()

        queue.add(Node(subClass, subParamIndex))

        while (queue.isNotEmpty()) {
            val (currentClass, varMapping) = queue.poll()

            if (currentClass == superClass) {
                // Found it. The superclass might have no type params.
                val superTypeParams = superClass.typeParameters
                if (superTypeParams.isEmpty()) return ParamMapping(emptyList())

                val indices = superTypeParams.map { tp -> varMapping[tp.name] ?: -1 }
                return ParamMapping(indices)
            }

            if (!visited.add(currentClass)) continue

            // Explore generic interfaces
            for (genIface in currentClass.genericInterfaces) {
                processGenericType(genIface, varMapping, superClass, visited)?.let { queue.add(it) }
            }

            // Explore generic superclass
            currentClass.genericSuperclass?.let { genSuper ->
                processGenericType(genSuper, varMapping, superClass, visited)?.let { queue.add(it) }
            }
        }

        // Shouldn't reach here if isAssignableFrom was true, but just in case:
        // the class is assignable but we couldn't trace generics (e.g. raw types).
        val superTypeParams = superClass.typeParameters
        return if (superTypeParams.isEmpty()) ParamMapping(emptyList())
        else ParamMapping(superTypeParams.map { -1 })
    }

    /**
     * Process a generic type (interface or superclass) encountered during BFS.
     * Returns a Node to enqueue if the type's raw class hasn't been visited yet.
     */
    private fun processGenericType(
        genType: java.lang.reflect.Type,
        currentVarMapping: Map<String, Int>,
        superClass: Class<*>,
        visited: Set<Class<*>>
    ): Node? {
        return when (genType) {
            is ParameterizedType -> {
                val rawClass = genType.rawType as? Class<*> ?: return null
                if (rawClass in visited) return null

                // Build a new var mapping: for each type argument of the parameterized type,
                // if it's a TypeVariable that we know the index of, propagate it.
                val rawTypeParams = rawClass.typeParameters
                val newMapping = mutableMapOf<String, Int>()
                genType.actualTypeArguments.forEachIndexed { i, arg ->
                    if (i < rawTypeParams.size) {
                        val targetName = rawTypeParams[i].name
                        when (arg) {
                            is TypeVariable<*> -> {
                                val idx = currentVarMapping[arg.name]
                                if (idx != null) newMapping[targetName] = idx
                            }
                            // Concrete types don't map to any subclass param — leave unmapped (-1 later).
                        }
                    }
                }
                Node(rawClass, newMapping)
            }
            is Class<*> -> {
                if (genType in visited) null
                else Node(genType, emptyMap())
            }
            else -> null
        }
    }

    private data class Node(val clazz: Class<*>, val varMapping: Map<String, Int>)
}
