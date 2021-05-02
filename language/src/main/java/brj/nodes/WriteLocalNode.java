package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;

@NodeChild(value = "expr", type = ExprNode.class)
@NodeField(name = "frameSlot", type = FrameSlot.class)
public abstract class WriteLocalNode extends ExprNode {

    protected WriteLocalNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract FrameSlot getFrameSlot();

    @Specialization
    public Object doExecute(VirtualFrame frame, Object expr) {
        frame.setObject(getFrameSlot(), expr);
        return expr;
    }

    public abstract Object execute(VirtualFrame frame);

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.WriteVariableTag.class || super.hasTag(tag);
    }
}
