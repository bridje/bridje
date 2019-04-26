@file:Suppress("unused")

package brj

fun <E> concat(vecs: List<List<E>>) = vecs.flatten()

fun <E> empty(vec: List<E>) = vec.isEmpty()