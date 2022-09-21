package brj.nodes

import brj.BridjeTypesGen
import brj.runtime.BridjeVar
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.profiles.ConditionProfile

abstract class DefxValueRootNode protected constructor(
    language: TruffleLanguage<*>?,
    frameDescriptor: FrameDescriptor?,
    private val sym: Symbol,
    private val defaultImplVar: BridjeVar
) : RootNode(language, frameDescriptor) {
    @Child
    private var dynObjs = DynamicObjectLibrary.getUncached()

    @Child
    private var indirectCallNode = Truffle.getRuntime().createIndirectCallNode()

    private val useDefaultProfile = ConditionProfile.createBinaryProfile()

    @Specialization
    fun doExecute(frame: VirtualFrame): Any = try {
        var fxMap = BridjeTypesGen.expectFxMap(frame.arguments[0])
        var fnValue: Any? = null
        while (fnValue == null && fxMap != null) {
            fnValue = dynObjs.getOrDefault(fxMap, sym, null)
            fxMap = fxMap.parent
        }
        if (useDefaultProfile.profile(fnValue == null)) {
            fnValue = defaultImplVar.value
        }
        indirectCallNode.call(BridjeTypesGen.expectBridjeFunction(fnValue).callTarget, *frame.arguments)
    } catch (e: UnexpectedResultException) {
        throw CompilerDirectives.shouldNotReachHere(e)
    }
}