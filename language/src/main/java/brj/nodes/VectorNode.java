package brj.nodes;

import brj.BridjeVector;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VectorNode extends ExprNode {

    private final ExprNode[] exprs;
    private final SourceSection loc;

    public VectorNode(ExprNode[] exprs, SourceSection loc) {
        this.exprs = exprs;
        this.loc = loc;
    }

    @Nullable
    @Override
    public SourceSection getLoc() {
        return loc;
    }

    @NotNull
    @Override
    @ExplodeLoop
    public Object execute(@NotNull VirtualFrame frame) {
        Object[] res = new Object[exprs.length];

        for (int i = 0; i < exprs.length; i++) {
            res[i] = exprs[i].execute(frame);
        }

        return new BridjeVector(res);
    }
}
