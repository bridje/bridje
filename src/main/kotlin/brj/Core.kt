@file:Suppress("unused")

package brj

fun <E> concat(vecs: List<List<E>>) = vecs.flatten()

fun <E> empty(vec: List<E>) = vec.isEmpty()
fun <E> count(vec: List<E>) = vec.size
fun <E> first(vec: List<E>) = vec.first()
fun <E> rest(vec: List<E>) = vec.drop(1)
