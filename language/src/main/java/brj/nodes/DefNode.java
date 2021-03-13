package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.Symbol;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DefNode extends ExprNode {
    private final Symbol sym;
    @Child
    private ExprNode exprNode;
    private final SourceSection loc;

    public DefNode(Symbol sym, ExprNode exprNode, SourceSection loc) {
        this.sym = sym;
        this.exprNode = exprNode;
        this.loc = loc;
    }

    @Nullable
    @Override
    public SourceSection getLoc() {
        return loc;
    }

    @TruffleBoundary
    private void setVar(BridjeContext ctx, Object val) {
        ctx.getBridjeEnv$language().setVar(sym, val);
    }

    @NotNull
    @Specialization
    public Object doExecute(@NotNull VirtualFrame frame,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        setVar(ctx, exprNode.execute(frame));
        return sym;
    }
}
