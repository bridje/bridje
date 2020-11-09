package brj.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LetNode extends ExprNode {

    @Children
    private final WriteLocalNode[] bindingNodes;

    @Child
    private ExprNode expr;

    private final SourceSection loc;

    public LetNode(WriteLocalNode[] bindingNodes, ExprNode expr, SourceSection loc) {
        this.bindingNodes = bindingNodes;
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
        for (WriteLocalNode bindingNode : bindingNodes) {
            bindingNode.execute(frame);
        }

        return expr.execute(frame);
    }
}
