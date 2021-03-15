package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeFunction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeChild(value = "fn", type = ExprNode.class)
@NodeChild(value = "args", type = ExecuteArrayNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class CallNode extends ExprNode {

    @Child
    private IndirectCallNode indirectCallNode = Truffle.getRuntime().createIndirectCallNode();

    protected CallNode(BridjeLanguage lang) {
        super(lang);
    }

    @NotNull
    @Specialization
    public Object doExecute(BridjeFunction fn, Object[] args) {
        return indirectCallNode.call(fn.getCallTarget(), args);
    }
}
