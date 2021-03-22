package brj.nodes;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

@NodeChild(value = "expr", type = ExprNode.class)
public abstract class ValueExprRootNode extends RootNode {
    protected ValueExprRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Specialization
    public Object execute(Object expr) {
        return expr;
    }
}
