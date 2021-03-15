package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "els", type = ExecuteArrayNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class CollNode extends ExprNode {
    protected CollNode(BridjeLanguage lang) {
        super(lang);
    }

    abstract Object executeColl(Object[] res);
}
