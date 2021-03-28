package brj.runtime

import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Shape
import com.oracle.truffle.api.interop.TruffleObject

class FxMap(shape: Shape, val parent: FxMap? = null) :
    DynamicObject(shape),
    TruffleObject {

    companion object {
        val DEFAULT_SHAPE: Shape = Shape.newBuilder().layout(FxMap::class.java).build()
    }
}