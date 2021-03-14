package brj.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "els", type = ExecuteArrayNode.class)
@NodeField(name = "loc", type = SourceSection.class)
public abstract class CollNode extends ExprNode {
    abstract Object executeColl(Object[] res);
}
