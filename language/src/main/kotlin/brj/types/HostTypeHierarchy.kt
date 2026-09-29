package brj.types

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.util.concurrent.ConcurrentHashMap

// The Java class hierarchy as the checker sees it: how many type parameters a class takes, and a supertype's
// type arguments in terms of a subclass's. Classes are loaded without being initialised, so checking a program
// runs none of its code.
internal object HostTypeHierarchy {

    // A supertype's type argument, in terms of the subclass's parameters: one of them, a class at arguments of
    // its own, as `Path` is `Iterable<Path>`, or unknown, as a wildcard is.
    sealed interface Arg {
        data class Param(val index: Int) : Arg
        data class Class(val name: String, val args: List<Arg>) : Arg
        data object Unknown : Arg
    }

    private fun load(name: String): java.lang.Class<*>? =
        try {
            java.lang.Class.forName(name, false, HostTypeHierarchy::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            null
        }

    private val arities = ConcurrentHashMap<String, Int>()

    // The number of type parameters the class declares, or 0 where the class is not on the classpath.
    fun arity(className: String): Int = arities.computeIfAbsent(className) { load(it)?.typeParameters?.size ?: 0 }

    // A cache entry: the supertype's arguments, or null where the subclass is not below it.
    private data class Supertype(val args: List<Arg>?)

    private val supertypes = ConcurrentHashMap<Pair<String, String>, Supertype>()

    // [sup]'s type arguments in terms of [sub]'s parameters, or null where [sub] is not a [sup].
    fun supertypeArgs(sub: String, sup: String): List<Arg>? =
        supertypes.computeIfAbsent(sub to sup) { Supertype(compute(sub, sup)) }.args

    private fun compute(subName: String, supName: String): List<Arg>? {
        val sub = load(subName) ?: return null
        val sup = load(supName) ?: return null
        if (!sup.isAssignableFrom(sub)) return null
        val params = sub.typeParameters.withIndex().associate { (i, tv) -> tv as TypeVariable<*> to Arg.Param(i) as Arg }
        // A class reached only through a raw supertype says nothing of the supertype's arguments.
        return walk(sub, params, sup) ?: sup.typeParameters.map { Arg.Unknown }
    }

    // Up the hierarchy from [c], whose parameters stand for [env], to [sup]'s arguments. Only supertypes below
    // [sup] are followed, so the walk ends.
    private fun walk(c: java.lang.Class<*>, env: Map<TypeVariable<*>, Arg>, sup: java.lang.Class<*>): List<Arg>? {
        if (c == sup) return sup.typeParameters.map { env[it] ?: Arg.Unknown }
        for (t in listOfNotNull(c.genericSuperclass) + c.genericInterfaces) {
            val raw = rawClass(t) ?: continue
            if (!sup.isAssignableFrom(raw)) continue
            val args = (t as? ParameterizedType)?.actualTypeArguments?.map { resolve(it, env) }
            val next = raw.typeParameters.withIndex().associate { (i, tv) -> tv as TypeVariable<*> to (args?.getOrNull(i) ?: Arg.Unknown) }
            walk(raw, next, sup)?.let { return it }
        }
        return null
    }

    private fun rawClass(t: java.lang.reflect.Type): java.lang.Class<*>? = when (t) {
        is java.lang.Class<*> -> t
        is ParameterizedType -> t.rawType as? java.lang.Class<*>
        else -> null
    }

    private fun resolve(t: java.lang.reflect.Type, env: Map<TypeVariable<*>, Arg>): Arg = when (t) {
        is TypeVariable<*> -> env[t] ?: Arg.Unknown
        is java.lang.Class<*> -> Arg.Class(t.name, emptyList())
        is ParameterizedType -> rawClass(t)?.let { raw -> Arg.Class(raw.name, t.actualTypeArguments.map { resolve(it, env) }) } ?: Arg.Unknown
        else -> Arg.Unknown
    }
}
