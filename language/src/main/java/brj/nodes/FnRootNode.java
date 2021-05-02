package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

@GenerateWrapper
public class FnRootNode extends RootNode implements InstrumentableNode {

    @Children
    private final WriteLocalNode[] writeArgNodes;

    @Child
    private ExprNode exprNode;

    protected FnRootNode(FnRootNode copy) {
        this(copy.getLanguage(BridjeLanguage.class), copy.getFrameDescriptor(),
            copy.writeArgNodes, copy.exprNode);
    }

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


    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new FnRootNodeWrapper(this, this, probe);
    }
}
