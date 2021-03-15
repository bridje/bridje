package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "value", type = Integer.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class IntNode extends ExprNode {
    protected IntNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract int getValue();

    @Specialization
    public int doExecute() {
        return getValue();
    }
}