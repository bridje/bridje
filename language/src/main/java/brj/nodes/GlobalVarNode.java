package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeVar;
import brj.runtime.GlobalVar;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "var", type = BridjeVar.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class GlobalVarNode extends ExprNode {
    public GlobalVarNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization(assumptions = "var.getAssumption().getAssumption()")
    public Object cachedExecute(@Cached("var.getValue()") Object value) {
        return value;
    }
}
