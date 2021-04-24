package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "value", type = Object.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class ConstantNode extends ExprNode {

    public ConstantNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public Object doExecute(Object value) {
        return value;
    }
}