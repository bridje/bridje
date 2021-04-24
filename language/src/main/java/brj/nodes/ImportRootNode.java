package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.runtime.Nil;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class ImportRootNode extends RootNode {

    @CompilationFinal(dimensions = 1)
    private final Symbol[] classes;

    public ImportRootNode(TruffleLanguage<?> language, Symbol[] classes) {
        super(language);
        this.classes = classes;
    }

    @Specialization
    @ExplodeLoop
    public Object doExecute(@CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        for (Symbol className : classes) {
            ctx.getBridjeEnv().importClass(className);
        }

        return Nil.INSTANCE;
    }
}
