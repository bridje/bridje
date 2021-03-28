package brj.nodes;

import brj.AnalyserKt;
import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

@NodeChild(value = "writeFxLocal", type = WriteLocalNode.class)
@NodeChild(value = "expr", type = ExprNode.class)
public abstract class ValueExprRootNode extends RootNode {
    protected ValueExprRootNode(BridjeLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Specialization
    public Object execute(Object _writeFxLocal, Object expr) {
        return expr;
    }

    public static ValueExprRootNode create(BridjeLanguage lang, FrameDescriptor frameDescriptor, ExprNode exprNode) {
        WriteLocalNode readFxMapNode = WriteLocalNodeGen.create(
            new ReadArgNode(lang, 0),
            frameDescriptor.findOrAddFrameSlot(AnalyserKt.getDEFAULT_FX_LOCAL()));
        return ValueExprRootNodeGen.create(lang, frameDescriptor, readFxMapNode, exprNode);
    }
}
