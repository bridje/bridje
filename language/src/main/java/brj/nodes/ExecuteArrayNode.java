package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.jetbrains.annotations.NotNull;

public class ExecuteArrayNode extends ExecutableNode {
    @Children
    private final ExecutableNode[] exprNodes;

    public ExecuteArrayNode(BridjeLanguage lang, ExecutableNode[] exprNodes) {
        super(lang);
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
