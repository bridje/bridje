package brj.builtins

import brj.BridjeLanguage
import brj.runtime.Anomaly.Companion.incorrect
import brj.runtime.BridjeNull
import brj.runtime.BridjeVector
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.RootNode

// TODO: replace with interop (:count member) when available
class CountNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val collection = frame.arguments[0]
        return try {
            interop.getArraySize(collection)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("count: not a collection")
        }
    }
}

// TODO: replace with interop (:first member) when available
class FirstNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val collection = frame.arguments[0]
        return try {
            val size = interop.getArraySize(collection)
            if (size == 0L) throw incorrect("first: empty collection")
            interop.readArrayElement(collection, 0)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("first: not a collection")
        }
    }
}

// TODO: replace with interop (:firstOrNull member) when available
class FirstOrNullNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any? {
        val collection = frame.arguments[0]
        return try {
            val size = interop.getArraySize(collection)
            if (size == 0L) BridjeNull
            else interop.readArrayElement(collection, 0)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("firstOrNull: not a collection")
        }
    }
}

// TODO: replace with interop (:rest member) when available
class RestNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val collection = frame.arguments[0]
        return try {
            val size = interop.getArraySize(collection)
            val els = List(maxOf(0, (size - 1).toInt())) { i ->
                interop.readArrayElement(collection, (i + 1).toLong())
            }
            BridjeVector(els)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("rest: not a collection")
        }
    }
}

// TODO: replace with interop (:cons member) when available
class ConsNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val element = frame.arguments[0]
        val collection = frame.arguments[1]
        return try {
            val size = interop.getArraySize(collection)
            val els = List((size + 1).toInt()) { i ->
                if (i == 0) element!!
                else interop.readArrayElement(collection, (i - 1).toLong())
            }
            BridjeVector(els)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("cons: second argument is not a collection")
        }
    }
}

// TODO: replace with interop (:empty? member) when available
class EmptyNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val collection = frame.arguments[0]
        return try {
            interop.getArraySize(collection) == 0L
        } catch (e: UnsupportedMessageException) {
            throw incorrect("empty?: not a collection")
        }
    }
}

class ConcatNode(language: BridjeLanguage) : RootNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    override fun execute(frame: VirtualFrame): Any {
        val vec1 = frame.arguments[0]
        val vec2 = frame.arguments[1]
        return try {
            val size1 = interop.getArraySize(vec1)
            val size2 = interop.getArraySize(vec2)
            val els = List((size1 + size2).toInt()) { i ->
                if (i < size1) interop.readArrayElement(vec1, i.toLong())
                else interop.readArrayElement(vec2, (i - size1).toLong())
            }
            BridjeVector(els)
        } catch (e: UnsupportedMessageException) {
            throw incorrect("concat: arguments must be collections")
        }
    }
}
