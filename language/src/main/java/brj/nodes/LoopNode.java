package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.source.SourceSection;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

public class LoopNode extends ExprNode {

    @Children
    private final WriteLocalNode[] writeLocalNodes;

    @Child
    private com.oracle.truffle.api.nodes.LoopNode loopBodyNode;

    private final SourceSection sourceSection;

    public LoopNode(BridjeLanguage lang, WriteLocalNode[] writeLocalNodes, ExprNode exprNode, SourceSection sourceSection) {
        super(lang);
        this.writeLocalNodes = writeLocalNodes;
        this.loopBodyNode = Truffle.getRuntime().createLoopNode(new LoopBodyNode(exprNode));
        this.sourceSection = sourceSection;
    }

    public static class LoopExitException extends ControlFlowException {
        private final Object returnValue;

        public LoopExitException(Object returnValue) {
            this.returnValue = returnValue;
        }
    }

    public static class LoopBodyNode extends Node implements RepeatingNode {

        private ExprNode exprNode;

        public LoopBodyNode(ExprNode exprNode) {
            this.exprNode = exprNode;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            try {
                throw new LoopExitException(exprNode.execute(frame));
            } catch (RecurNode.RecurException e) {
                return true;
            }
        }
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (WriteLocalNode writeLocalNode : writeLocalNodes) {
            writeLocalNode.execute(frame);
        }

        try {
            loopBodyNode.execute(frame);
            throw shouldNotReachHere("loop didn't exit properly");
        } catch (LoopExitException e) {
            return e.returnValue;
        }
    }
}
