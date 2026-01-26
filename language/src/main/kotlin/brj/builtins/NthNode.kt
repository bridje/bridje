package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.UnsupportedMessageException

abstract class NthNode(language: BridjeLanguage) : BinaryOpNode(language) {
    @Child
    private var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

    @Specialization
    fun nth(collection: Any, index: Long): Any? {
        return try {
            interop.readArrayElement(collection, index)
        } catch (e: UnsupportedMessageException) {
            throw RuntimeException("nth: not a collection")
        } catch (e: InvalidArrayIndexException) {
            throw RuntimeException("nth: index out of bounds: $index")
        }
    }
}
