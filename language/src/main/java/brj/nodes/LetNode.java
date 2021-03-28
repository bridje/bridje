package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

public class LetNode extends ExprNode {

    @Children
    private final WriteLocalNode[] bindingNodes;

    @Child
    private ExprNode expr;

    private final SourceSection sourceSection;

    public LetNode(BridjeLanguage lang, WriteLocalNode[] bindingNodes, ExprNode expr, SourceSection sourceSection) {
        super(lang);
        this.bindingNodes = bindingNodes;
        this.expr = expr;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @NotNull
    @Override
    @ExplodeLoop
    public Object execute(@NotNull VirtualFrame frame) {
        for (WriteLocalNode bindingNode : bindingNodes) {
            bindingNode.execute(frame);
        }

        return expr.execute(frame);
    }
}
