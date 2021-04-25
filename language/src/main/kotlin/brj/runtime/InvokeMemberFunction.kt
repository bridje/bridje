package brj.runtime

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop

@ExportLibrary(InteropLibrary::class)
class InvokeMemberFunction(val sym: Symbol) : TruffleObject {
    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    class Execute {
        companion object {
            @Specialization(guards = ["args.length == cachedArgCount"])
            @JvmStatic
            @ExplodeLoop
            fun doExecute(
                imo: InvokeMemberFunction,
                args: Array<*>,
                @Cached("args.length") cachedArgCount: Int,
                @CachedLibrary(limit = "3") interop: InteropLibrary
            ): Any? {
                CompilerAsserts.compilationConstant<Int>(cachedArgCount)
                CompilerAsserts.compilationConstant<String>(imo.sym.local)

                val invokeArgs = arrayOfNulls<Any>(cachedArgCount - 1)

                for (i in (1 until args.size)) {
                    invokeArgs[i - 1] = args[i]
                }

                return interop.invokeMember(args[0], imo.sym.local, *invokeArgs)
            }

        }
    }
}