package brj.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DoNode extends ExprNode {
    @Children
    private final ExprNode[] exprs;

    @Child
    private ExprNode expr;

    private final SourceSection loc;

    public DoNode(ExprNode[] exprs, ExprNode expr, SourceSection loc) {
        this.exprs = exprs;
        this.expr = expr;
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
        for (ExprNode exprNode : exprs) {
            exprNode.execute(frame);
        }

        return expr.execute(frame);
    }
}
