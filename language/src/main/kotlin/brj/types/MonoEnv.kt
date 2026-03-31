package brj.types

import brj.analyser.LocalVar

internal typealias MonoEnv = Map<LocalVar, Type>

internal fun Collection<MonoEnv>.groupReqmts(): Map<LocalVar, Pair<Type, Collection<Type>>> =
    flatMap { it.entries }
        .groupBy { it.key }
        .mapValues {
            val types = it.value.map { e -> e.value }
            if (types.size == 1) {
                Pair(types.single(), types)
            } else {
                Pair(freshType(), types)
            }
        }
