package brj.builtins

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop

@ExportLibrary(InteropLibrary::class)
class InstantiateFunction(val obj: TruffleObject) : TruffleObject {
    @get:ExportMessage
    val isExecutable = true

    @ExportMessage
    class Execute {
        companion object {
            @Specialization
            @JvmStatic
            @ExplodeLoop
            fun doExecute(
                fn: InstantiateFunction,
                args: Array<*>,
                @CachedLibrary(limit = "3") interop: InteropLibrary
            ): Any? {
                CompilerAsserts.compilationConstant<String>(fn.obj)

                return interop.instantiate(fn.obj, *args)
            }
        }
    }
}