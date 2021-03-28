package brj.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNull;

@NodeChild(value = "expr", type = ExprNode.class)
@NodeField(name = "frameSlot", type = FrameSlot.class)
public abstract class WriteLocalNode extends Node {

    public abstract FrameSlot getFrameSlot();

    @Specialization
    public Object doExecute(@NotNull VirtualFrame frame, Object expr) {
        frame.setObject(getFrameSlot(), expr);
        return expr;
    }

    public abstract Object execute(VirtualFrame frame);
}
