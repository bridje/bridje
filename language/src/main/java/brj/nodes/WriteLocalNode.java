package brj.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNull;

public class WriteLocalNode extends Node {

    private final FrameSlot frameSlot;

    @Child
    private ExprNode expr;

    public WriteLocalNode(FrameSlot frameSlot, ExprNode expr) {
        this.frameSlot = frameSlot;
        this.expr = expr;
    }

    public void execute(@NotNull VirtualFrame frame) {
        frame.setObject(frameSlot, expr.execute(frame));
    }
}
