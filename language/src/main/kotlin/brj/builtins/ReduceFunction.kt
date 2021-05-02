package brj.builtins

import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.UnexpectedResultException

@ExportLibrary(InteropLibrary::class)
object ReduceFunction : TruffleObject {
    private val interop = InteropLibrary.getUncached()

    @get:ExportMessage
    val isExecutable = true

    @ExportMessage
    fun execute(args: Array<*>): Any? {
        if (args.size != 3) throw ArityException.create(3, args.size)

        val (f, init, coll) = args
        if (!interop.isExecutable(f)) throw UnexpectedResultException(f)
        if (!interop.hasIterator(coll)) throw UnexpectedResultException(coll)

        val fs = InteropLibrary.getUncached(f)

        val iterator = interop.getIterator(coll)
        val iterators = InteropLibrary.getUncached(iterator)
        var res = init

        while (iterators.hasIteratorNextElement(iterator)) {
            res = fs.execute(f, res, iterators.getIteratorNextElement(iterator))
        }

        return res
    }
}