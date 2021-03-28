package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

public class FnRootNode extends RootNode {

    @Children
    private final WriteLocalNode[] writeArgNodes;

    @Child
    private ExprNode exprNode;

    public FnRootNode(BridjeLanguage lang, FrameDescriptor frameDescriptor,
                      WriteLocalNode[] writeArgNodes, ExprNode exprNode) {
        super(lang, frameDescriptor);
        this.writeArgNodes = writeArgNodes;
        this.exprNode = exprNode;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (WriteLocalNode writeArgNode : writeArgNodes) {
            writeArgNode.execute(frame);
        }

        return exprNode.execute(frame);
    }
}
