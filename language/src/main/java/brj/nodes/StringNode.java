package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "value", type = String.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class StringNode extends ExprNode {

    public StringNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public String doExecute(String value) {
        return value;
    }
}