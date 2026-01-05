package brj.runtime

import brj.NsEnv
import brj.analyser.NsDecl

/**
 * Immutable global environment containing all namespaces and dependency tracking.
 */
data class GlobalEnv(
    val namespaces: Map<String, NsEnv> = emptyMap(),
    val quarantined: Map<String, QuarantinedNs> = emptyMap(),
    val reverseDependencies: Map<String, Set<String>> = emptyMap()
) {
    /**
     * Information about a quarantined namespace that can be re-evaluated.
     */
    data class QuarantinedNs(
        val nsDecl: NsDecl,
        val forms: List<brj.Form>
    )

    /**
     * Register a namespace and update reverse dependencies.
     */
    fun withNamespace(name: String, nsEnv: NsEnv): GlobalEnv {
        val oldNsEnv = namespaces[name]
        val oldDeps = oldNsEnv?.requires?.values?.map { it.nsDecl?.name }?.filterNotNull()?.toSet().orEmpty()
        val newDeps = nsEnv.requires.values.mapNotNull { it.nsDecl?.name }.toSet()

        // Calculate changes in dependencies
        val removedDeps = oldDeps - newDeps
        val addedDeps = newDeps - oldDeps

        // Update reverse dependencies
        var newReverseDeps = reverseDependencies

        // Remove this namespace from old dependencies
        removedDeps.forEach { depNameFq ->
            val currentDeps = newReverseDeps[depNameFq].orEmpty() - name
            newReverseDeps = newReverseDeps + (depNameFq to currentDeps)
        }

        // Add this namespace to new dependencies
        addedDeps.forEach { depNameFq ->
            val currentDeps = newReverseDeps[depNameFq].orEmpty() + name
            newReverseDeps = newReverseDeps + (depNameFq to currentDeps)
        }

        // Remove from quarantine if it was there
        return copy(
            namespaces = namespaces + (name to nsEnv),
            quarantined = quarantined - name,
            reverseDependencies = newReverseDeps
        )
    }

    /**
     * Invalidate a namespace and all its dependents recursively.
     */
    fun invalidateNamespace(name: String): GlobalEnv {
        if (name !in namespaces) return this

        val nsEnv = namespaces[name]!!
        val dependents = reverseDependencies[name].orEmpty()

        // Quarantine this namespace if it has nsDecl and forms
        var newEnv = if (nsEnv.nsDecl != null && nsEnv.forms.isNotEmpty()) {
            copy(
                namespaces = namespaces - name,
                quarantined = quarantined + (name to QuarantinedNs(nsEnv.nsDecl, nsEnv.forms))
            )
        } else {
            copy(namespaces = namespaces - name)
        }

        // Remove from reverse dependencies
        nsEnv.requires.values.mapNotNull { it.nsDecl?.name }.forEach { depName ->
            val currentDeps = newEnv.reverseDependencies[depName].orEmpty() - name
            newEnv = newEnv.copy(
                reverseDependencies = newEnv.reverseDependencies + (depName to currentDeps)
            )
        }

        // Recursively invalidate all dependents
        dependents.forEach { dependent ->
            newEnv = newEnv.invalidateNamespace(dependent)
        }

        return newEnv
    }
}
