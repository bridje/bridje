package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.MonoType;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
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
        CompilerAsserts.neverPartOfCompilation();
        var sym = getSym();
        ctx.getBridjeEnv().setVar(sym, getType(), val);
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
