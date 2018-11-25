package brj

import brj.BrjLanguage.Companion.getCtx
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ForeignAccess.sendExecute
import com.oracle.truffle.api.interop.ForeignAccess.sendRead
import com.oracle.truffle.api.interop.Message
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.nodes.ExplodeLoop

internal abstract class JavaInteropNode: ValueNode() {
    abstract override fun execute(frame: VirtualFrame): TruffleObject
}

internal class JavaStaticReadNode(javaImport: JavaImport) : JavaInteropNode() {
    val clazzObj = getCtx().truffleEnv.lookupHostSymbol(javaImport.clazz.name) as TruffleObject
    val name = javaImport.sym.name.nameStr

    @Child
    var readNode = Message.READ.createNode()

    override fun execute(frame: VirtualFrame) = sendRead(readNode, clazzObj, name) as TruffleObject
}

internal class JavaExecuteNode(@Child var fnNode: JavaInteropNode, javaImport: JavaImport) : ValueNode() {
    @Children
    val argNodes = (0 until (javaImport.type.monoType as FnType).paramTypes.size).map { idx -> ReadArgNode(idx) }.toTypedArray()

    @Child
    var executeNode = Message.EXECUTE.createNode()

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val params = arrayOfNulls<Any>(argNodes.size)

        for (i in argNodes.indices) {
            params[i] = argNodes[i].execute(frame)
        }

        return sendExecute(executeNode, fnNode.execute(frame), *params)
    }
}

internal object JavaImportEmitter {
    fun emitJavaImport(javaImport: JavaImport): BridjeFunction =
        BridjeFunction(makeRootNode(JavaExecuteNode(JavaStaticReadNode(javaImport), javaImport)))
}
