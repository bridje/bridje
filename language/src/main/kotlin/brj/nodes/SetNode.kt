package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeSet
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class SetNode(lang: BridjeLanguage, loc: SourceSection?) : CollNode(lang, loc) {
    @Specialization
    override fun executeColl(els: Array<Any>) = BridjeSet(els)
}