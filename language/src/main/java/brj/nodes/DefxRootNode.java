package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.MonoType;
import brj.runtime.Symbol;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "sym", type = Symbol.class)
@NodeField(name = "type", type = MonoType.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefxRootNode extends RootNode {

    public DefxRootNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract Symbol getSym();
    public abstract MonoType getType();

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        Symbol sym = getSym();
        ctx.getBridjeEnv().defx(sym, getType());
        return sym;
    }
}
