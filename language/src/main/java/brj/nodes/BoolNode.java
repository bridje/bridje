package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "value", type = Boolean.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class BoolNode extends ExprNode {
    protected BoolNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract boolean getValue();

    @NotNull
    @Specialization
    public boolean doExecute() {
        return getValue();
    }
}
