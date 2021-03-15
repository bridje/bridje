package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "value", type = Object.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class GlobalVarNode extends ExprNode {
    public GlobalVarNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract Object getValue();

    @NotNull
    @Specialization
    public Object execute() {
        return getValue();
    }
}
