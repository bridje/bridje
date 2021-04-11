package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class RecurNode extends ExprNode {

    @Children
    private final WriteLocalNode[] writeLocalNodes;

    public RecurNode(BridjeLanguage lang, WriteLocalNode[] writeLocalNodes) {
        super(lang);
        this.writeLocalNodes = writeLocalNodes;
    }

    public static class RecurException extends ControlFlowException {
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (WriteLocalNode writeLocalNode : writeLocalNodes) {
            writeLocalNode.execute(frame);
        }

        throw new RecurException();
    }
}
