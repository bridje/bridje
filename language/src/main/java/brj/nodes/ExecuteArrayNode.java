package brj.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.jetbrains.annotations.NotNull;

public class ExecuteArrayNode extends ExprNode {
    @Children
    private final ExprNode[] exprNodes;

    public ExecuteArrayNode(ExprNode[] exprNodes) {
        this.exprNodes = exprNodes;
    }

    @NotNull
    @Override
    @ExplodeLoop
    public Object[] execute(@NotNull VirtualFrame frame) {
        Object[] res = new Object[exprNodes.length];
        for (int i = 0; i < exprNodes.length; i++) {
            res[i] = exprNodes[i].execute(frame);
        }
        return res;
    }
}
