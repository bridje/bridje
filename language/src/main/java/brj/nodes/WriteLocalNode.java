package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "frameSlot", type = FrameSlot.class)
@NodeChild(value = "expr", type = ExprNode.class)
public abstract class WriteLocalNode extends Node {

    public abstract FrameSlot getFrameSlot();

    @Specialization
    public void doExecute(@NotNull VirtualFrame frame, Object expr) {
        frame.setObject(getFrameSlot(), expr);
    }

    public abstract void execute(VirtualFrame frame);
}
