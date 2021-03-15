package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "sym", type = Symbol.class)
@NodeChild(value = "expr", type = ExprNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefNode extends ExprNode {

    public DefNode(BridjeLanguage lang) {
        super(lang);
    }

    @TruffleBoundary
    private void setVar(BridjeContext ctx, Symbol sym, Object val) {
        ctx.getBridjeEnv().setVar(sym, val);
    }

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @Cached("sym") Symbol sym,
                            Object exprVal,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        setVar(ctx, sym, exprVal);
        return sym;
    }
}
