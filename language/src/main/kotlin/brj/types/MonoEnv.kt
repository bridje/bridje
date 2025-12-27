package brj.types

import brj.LocalVar

internal typealias MonoEnv = Map<LocalVar, Type>

internal fun Collection<MonoEnv>.groupReqmts(): Map<LocalVar, Pair<Type, Collection<Type>>> =
    flatMap { it.entries }
        .groupBy { it.key }
        .mapValues {
            val tv = TypeVar()
            Pair(freshType(tv), it.value.map { e -> e.value })
        }
