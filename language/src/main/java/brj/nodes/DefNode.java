package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.MonoType;
import brj.runtime.GlobalVar;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "sym", type = Symbol.class)
@NodeField(name = "type", type = MonoType.class)
@NodeChild(value = "expr", type = ExprNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefNode extends ExprNode {

    public DefNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract Symbol getSym();
    public abstract MonoType getType();

    @TruffleBoundary
    private Object setVar(BridjeContext ctx, Object val) {
        var sym = getSym();
        ctx.getBridjeEnv().setVar(sym, new GlobalVar(sym, getType(), val));
        return val;
    }

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            Object exprVal,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        return setVar(ctx, exprVal);
    }
}
