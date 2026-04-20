package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.StopIterationException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeSet(val els: Set<Any>, override val meta: BridjeRecord = BridjeRecord.EMPTY) : TruffleObject, Meta<BridjeSet> {

    companion object {
        val EMPTY: BridjeSet = BridjeSet(emptySet())

        @JvmStatic
        fun count(set: BridjeSet): Long = set.els.size.toLong()

        @JvmStatic
        fun isEmpty(set: BridjeSet): Boolean = set.els.isEmpty()

        @JvmStatic
        fun contains(set: BridjeSet, el: Any): Boolean = set.els.contains(el)

        @JvmStatic
        fun conj(el: Any, set: BridjeSet): BridjeSet {
            if (el in set.els) return set
            val next = LinkedHashSet<Any>(set.els.size + 1)
            next.addAll(set.els)
            next.add(el)
            return BridjeSet(next)
        }

        @JvmStatic
        fun disj(el: Any, set: BridjeSet): BridjeSet {
            if (el !in set.els) return set
            val next = LinkedHashSet<Any>(set.els.size)
            for (x in set.els) if (x != el) next.add(x)
            return BridjeSet(next)
        }

        @JvmStatic
        fun union(a: BridjeSet, b: BridjeSet): BridjeSet {
            if (a.els.isEmpty()) return b
            if (b.els.isEmpty()) return a
            val next = LinkedHashSet<Any>(a.els.size + b.els.size)
            next.addAll(a.els)
            next.addAll(b.els)
            return BridjeSet(next)
        }

        @JvmStatic
        fun fromVec(vec: BridjeVector): BridjeSet {
            if (vec.els.isEmpty()) return EMPTY
            val next = LinkedHashSet<Any>(vec.els.size)
            next.addAll(vec.els)
            return BridjeSet(next)
        }
    }

    override fun withMeta(newMeta: BridjeRecord?): BridjeSet = BridjeSet(els, newMeta ?: BridjeRecord.EMPTY)

    @ExportMessage
    fun hasIterator() = true

    @ExportMessage
    fun getIterator(): Any = BridjeSetIterator(els.iterator())

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean): String {
        val interop = InteropLibrary.getUncached()
        return "#{${els.joinToString(" ") { interop.toDisplayString(it) as String }}}"
    }
}

@ExportLibrary(InteropLibrary::class)
class BridjeSetIterator(private val iter: Iterator<Any>) : TruffleObject {

    @ExportMessage
    fun isIterator() = true

    @ExportMessage
    @TruffleBoundary
    fun hasIteratorNextElement(): Boolean = iter.hasNext()

    @ExportMessage
    @TruffleBoundary
    @Throws(StopIterationException::class)
    fun getIteratorNextElement(): Any {
        if (!iter.hasNext()) throw StopIterationException.create()
        return iter.next()
    }
}
