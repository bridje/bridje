package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

public class FnRootNode extends RootNode {

    private final FrameSlot[] frameSlots;
    private final ExprNode exprNode;

    public FnRootNode(BridjeLanguage lang, FrameDescriptor frameDescriptor,
                      FrameSlot[] frameSlots, ExprNode exprNode) {
        super(lang, frameDescriptor);
        this.frameSlots = frameSlots;
        this.exprNode = exprNode;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        var args = frame.getArguments();

        for (int i = 0; i < frameSlots.length; i++) {
            frame.setObject(frameSlots[i], args[i]);
        }

        return exprNode.execute(frame);
    }
}
