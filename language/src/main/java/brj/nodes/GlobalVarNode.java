package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.GlobalVar;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "globalVar", type = GlobalVar.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class GlobalVarNode extends ExprNode {
    public GlobalVarNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract GlobalVar getGlobalVar();

    @NotNull
    @Specialization
    public Object execute() {
        return getGlobalVar().getValue();
    }
}
