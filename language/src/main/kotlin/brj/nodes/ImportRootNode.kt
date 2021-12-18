package brj.nodes

import brj.BridjeLanguage
import brj.runtime.Nil
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

private val CTX_REF = ContextReference.create(BridjeLanguage::class.java)

abstract class ImportRootNode(
    lang: TruffleLanguage<*>,
    private val loc: SourceSection?,
    @field:CompilationFinal(dimensions = 1) private val classes: Array<Symbol>
) : RootNode(lang) {

    @Specialization
    @ExplodeLoop
    fun doExecute(): Any {
        val ctx = CTX_REF[this]
        for (className in classes) {
            ctx.importClass(className)
        }
        return Nil
    }

    override fun getSourceSection() = loc
}