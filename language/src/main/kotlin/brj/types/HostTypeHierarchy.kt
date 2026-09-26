package brj.types

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

internal object HostTypeHierarchy {

    private data class ParamMapping(val indices: List<Int>)

    /** Sentinel for "not a subtype" since ConcurrentHashMap cannot store null values. */
    private val NOT_A_SUBTYPE = ParamMapping(listOf(Int.MIN_VALUE))

    private val cache = ConcurrentHashMap<Pair<String, String>, ParamMapping>()

    fun <T> mapSupertypeArgs(subClassName: String, superClassName: String, subArgs: List<T>, fresh: () -> T): List<T>? {
        val mapping = cache.computeIfAbsent(subClassName to superClassName) { (sub, sup) ->
            computeMapping(sub, sup) ?: NOT_A_SUBTYPE
        }

        if (mapping === NOT_A_SUBTYPE) return null

        return if (mapping.indices.isEmpty()) {
            emptyList()
        } else {
            mapping.indices.map { idx ->
                if (idx in subArgs.indices) subArgs[idx]
                else fresh()
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

        val subTypeParams = subClass.typeParameters
        val subParamIndex = subTypeParams.withIndex().associate { (i, tv) -> tv.name to i }

        val queue = LinkedList<Node>()
        val visited = mutableSetOf<Class<*>>()

        queue.add(Node(subClass, subParamIndex))

        while (queue.isNotEmpty()) {
            val (currentClass, varMapping) = queue.poll()

            if (currentClass == superClass) {
                val superTypeParams = superClass.typeParameters
                if (superTypeParams.isEmpty()) return ParamMapping(emptyList())

                val indices = superTypeParams.map { tp -> varMapping[tp.name] ?: -1 }
                return ParamMapping(indices)
            }

            if (!visited.add(currentClass)) continue

            for (genIface in currentClass.genericInterfaces) {
                processGenericType(genIface, varMapping, superClass, visited)?.let { queue.add(it) }
            }

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
