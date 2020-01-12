package brj.runtime

import brj.emitter.BridjeObject
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.RootNode

@ExportLibrary(InteropLibrary::class)
internal class BridjeFunction(val rootNode: RootNode, val lexObj: Any? = null) : BridjeObject {
    internal val callTarget = Truffle.getRuntime().createCallTarget(rootNode)

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    fun execute(args: Array<*>): Any? {
        val argsWithScope = arrayOfNulls<Any>(args.size + 1)
        System.arraycopy(args, 0, argsWithScope, 1, args.size)
        argsWithScope[0] = lexObj
        return callTarget.call(*argsWithScope)
    }
}