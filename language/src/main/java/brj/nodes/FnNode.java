package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeFunction;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "fn", type = BridjeFunction.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class FnNode extends ExprNode {
    public FnNode(BridjeLanguage lang) {
        super(lang);
    }

    protected abstract BridjeFunction getFn();

    @NotNull
    @Specialization
    public Object doExecute() {
        return getFn();
    }
}
