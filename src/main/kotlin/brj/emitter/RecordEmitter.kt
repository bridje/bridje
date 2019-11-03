package brj.emitter

import brj.BridjeContext
import brj.Loc
import brj.runtime.RecordKey
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Layout
import com.oracle.truffle.api.`object`.ObjectType
import com.oracle.truffle.api.`object`.Property
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
internal class RecordObject(private val truffleEnv: TruffleLanguage.Env, val keys: List<RecordKey>, val dynamicObject: DynamicObject) : BridjeObject {
    private val keyStrings by lazy {
        keys.associate { it.sym.toString() to it.sym }
    }

    @CompilerDirectives.TruffleBoundary
    override fun toString(): String = "{${keys.joinToString(", ") { key -> "${key.sym} ${dynamicObject[key.sym.toString()]}" }}}"

    @ExportMessage
    fun hasMembers() = true

    @Suppress("UNUSED_PARAMETER")
    @ExportMessage
    fun getMembers(includeInternal: Boolean) = truffleEnv.asGuestValue(keyStrings.keys.toList())!!

    @ExportMessage
    fun isMemberReadable(name: String) = keyStrings.keys.contains(name)

    @ExportMessage
    fun readMember(name: String) = dynamicObject[name]!!
}

internal data class RecordKeyReadNode(val recordKey: RecordKey) : ValueNode() {
    override val loc: Loc? = null

    @Child
    var readArgNode = ReadArgNode(0)

    override fun execute(frame: VirtualFrame) = BridjeTypesGen.expectRecordObject(readArgNode.execute(frame)).dynamicObject[recordKey.sym.toString()]!!
}

internal typealias RecordObjectFactory = (Array<Any?>) -> RecordObject

internal class RecordEmitter(val ctx: BridjeContext) {
    companion object {
        private val LAYOUT = Layout.createLayout()!!
    }

    internal data class RecordObjectType(val keys: Set<RecordKey>) : ObjectType()

    internal fun recordObjectFactory(keys: List<RecordKey>): RecordObjectFactory {
        val allocator = LAYOUT.createAllocator()
        var shape = LAYOUT.createShape(RecordObjectType(keys.toSet()))

        keys.forEach { key ->
            shape = shape.addProperty(Property.create(key.sym.toString(), allocator.locationForType(key.type.javaType), 0))
        }

        val factory = shape.createFactory()

        return { vals ->
            RecordObject(ctx.truffleEnv, keys, factory.newInstance(*vals))
        }
    }

    internal fun emitRecordKey(recordKey: RecordKey) = ValueExprEmitter.BridjeFunction(ctx.makeRootNode(RecordKeyReadNode(recordKey)))
}


