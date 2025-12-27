package brj.nodes

import brj.BridjeLanguage
import brj.BridjeNode
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.source.SourceSection

@NodeChild(value = "values", type = ExecuteArrayNode::class)
abstract class RecordNode(
    private val fieldNames: Array<String>,
    loc: SourceSection? = null
) : BridjeNode(loc) {

    @Specialization
    fun createRecord(values: Array<Any>,
                     @Bind language: BridjeLanguage): BridjeRecord {
        val record = BridjeRecord(language.recordShape)
        val objectLibrary = DynamicObjectLibrary.getUncached()
        for (i in fieldNames.indices) {
            objectLibrary.put(record, fieldNames[i], values[i])
        }
        return record
    }

    abstract override fun execute(frame: VirtualFrame): Any?
}
